package com.eigenpi.btjoystick;

// cristinel.ababei, december 2020
// this project is just a bluetooth terminal that can be used to send and receive
// data; it is meant to be used mainly for receiving data from a couple of sensors
// connected to an Arduino, which has also connected an HC-06 bluetooth module;
// communication between the Android device and the Arduino is done via BT;
//
// the data from the Arduino is sent as a string like this: <xyz>;
// this format starts with ‘<’ and ends with ‘>’ to mark the beginning and end of the packet;
// that is how the Android app decodes the data;
// this project integrates adapted portions from:
// https://wingoodharry.wordpress.com/2014/04/15/android-sendreceive-data-with-arduino-using-bluetooth-part-2/
// Note: I am not actually using the receiver portion; kept
// here for possible later use; currently only the transmitter
// portion is used to transmit joystick position data;
//
// Important Note:
// the Bluetooth is worked on through a thread; the TX part just works through the
// thread; the RX part processes the messages received within the thread via a
// static handler; that is because data received needs to update the main thread/activity
// UI (which is known not to be thread safe);

import android.os.Bundle;
import android.os.Message;
import android.os.Handler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.UUID;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
//import android.app.ActionBar;


public class MainActivity extends Activity {

    // (1) Joystick
    TextView txtX, txtY;
    JoystickView joystick;

    // (2) TX
    TextView txtStringSent;
    private My_Connected_Thread m_connected_thread; // used for transmitting?

    // (3) RX
    TextView txtStringReceived, txtStringLength;
    TextView sensorView0, sensorView1, sensorView2; // sensor data displayed here;

    MyStaticHandler bt_static_handler; // used for receiving?
    final int handlerState = 0; // used to identify handler message;
    private StringBuilder recDataString = new StringBuilder();
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;

    // (4) others
    // UUID service - the type of Bluetooth device that the BT module is;
    // very likely yours will be the same, if not google UUID for your manufacturer;
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // MAC address of Bluetooth module;
    private static String mac_address;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        // (1) Joystick side;
        txtX = (TextView)findViewById(R.id.TextViewX);
        txtY = (TextView)findViewById(R.id.TextViewY);
        joystick = (JoystickView)findViewById(R.id.joystickView);
        joystick.setOnJoystickMovedListener(_listener);

        // (2) TX side;
        // link textView to respective view,
        txtStringSent = (TextView) findViewById(R.id.txtDataSent);

        // (3) RX side;
        // this views display the length-of and the whole string received;
        // not really needed for the self-balancing project application
        // because we do not send anything back from the robot to this app; yet;
        // I keep this here as a place holder for future features;
        txtStringLength = (TextView) findViewById(R.id.txtDataLength);
        txtStringReceived = (TextView) findViewById(R.id.txtDataReceived);


        // (4) get BlueTooth adapter; check on the BT device state;
        // the btAdapter will be used to create the bt_device, which will then
        // be used to create the btSocket;
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        // use a static handler to avoid memory leaks?
        // this will make use of handleMessage(Message msg) to get the received
        // string of bytes and split it into individual sensor values and display
        // those in their text views;
        bt_static_handler = new MyStaticHandler(this);

        // check if btAdapter, the Android BT device, is enabled/available;
        check_BT_state();
    }


    private JoystickMovedListener _listener = new JoystickMovedListener() {
        @Override
        public void OnMoved(int pan, int tilt) { // X, Y
            txtX.setText(Integer.toString(pan));
            txtY.setText(Integer.toString(tilt));

            // here, we construct the special byte we send to the robot;
            // we set different bits of the byte to be sent based on how the
            //  joystick position dictates;
            //  for example, for joystick position:
            //  left, byte sent is 1 (in decimal)    : B00000001
            //  right, byte sent is 2 (in decimal)   : B00000010
            //  forward, byte sent is 4 (in decimal) : B00000100
            //  back, byte sent is 8 (in decimal)    : B00001000
            //  forward-right, byte sent is 6 (in decimal): B00000110 as combination of forward and right
            // etc.
            int send_byte = 0b00000000; // captures joystick position
            if (tilt < 0) send_byte |= 0b00000100; // forward when tilt is negative
            if (tilt > 0) send_byte |= 0b00001000; // back
            if (pan > 0) send_byte  |= 0b00000001; // left when tilt is negative
            if (pan < 0) send_byte  |= 0b00000010; // right
            txtStringSent.setText( Integer.toString(send_byte)); // display data sent in the TX section of layout;

            m_connected_thread.write( Integer.toString(send_byte)); // send out the data via BT;
            //Toast.makeText(getBaseContext(), "Sending data via BT", Toast.LENGTH_SHORT).show();
        }
        @Override
        public void OnReleased() {
            txtX.setText("released");
            txtY.setText("released");
        }
        @Override
        public void OnReturnedToCenter() {
            txtX.setText("stopped");
            txtY.setText("stopped");
        };
    };


    @Override
    public void onResume() {
        super.onResume();
        // (1) get MAC address from BTDeviceListActivity via intent;
        Intent intent = getIntent();
        mac_address = intent.getStringExtra(BTDeviceListActivity.EXTRA_DEVICE_ADDRESS);

        // (2) create BT device and set the MAC address;
        BluetoothDevice bt_device = btAdapter.getRemoteDevice(mac_address);
        try {
            btSocket = create_BT_socket(bt_device);
        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "BT socket creation failed", Toast.LENGTH_LONG).show();
        }

        // (3) establish the Bluetooth socket connection;
        try {
            btSocket.connect();
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                // insert code to deal with this;
            }
        }

        // (4)
        m_connected_thread = new My_Connected_Thread(btSocket);
        m_connected_thread.start();
        // send a character when resuming or beginning transmission to check device is connected;
        // if it's not, an exception will be thrown and finish() will be called;
        //m_connected_thread.write("x");
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            // don't leave Bluetooth sockets open when leaving activity;
            btSocket.close();
        } catch (IOException e2) {
            // insert code to deal with this;
        }
    }

    // create connection with BT device using UUID;
    private BluetoothSocket create_BT_socket(BluetoothDevice device) throws IOException {
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    // check if the Android BT device is enabled/available and prompts user to turned
    // it ON if it is not so;
    private void check_BT_state() {
        if (btAdapter == null) {
            Toast.makeText(getBaseContext(), "Device does not support BlueTooth", Toast.LENGTH_LONG).show();
        } else {
            if (btAdapter.isEnabled()) {
                // nothing here;
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    // create a new static class for handler;
    // this is declared here static in order to circumvent some Warning that has to do
    // with issues about memory leaks and garbage collection when working with Handlers;
    // this approach is done such that static inner class, MyStaticHandler, does not hold an
    // implicit reference to the outer class, MainActivity;
    // some description and more info here:
    // https://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html
    // https://stackoverflow.com/questions/11407943/this-handler-class-should-be-static-or-leaks-might-occur-incominghandler
    //
    // also, note that because we extend the Handler class, we must implement the handleMessage(Message msg)
    // public method, such that it receives messages! as mentioned here:
    // https://developer.android.com/reference/android/os/Handler.html
    //
    // some background info on Thread Handlers:
    // http://www.techotopia.com/index.php/A_Basic_Overview_of_Android_Threads_and_Thread_handlers
    // rule1: never perform time-consuming operations on the main thread of an application;
    // rule2: the code within a separate thread must never directly update anything in the UI;
    // any changes to the UI must always be performed from within the main thread because
    // the Android UI toolkit is not thread-safe;
    // because in our case the code executing in a thread needs to interact with the UI,
    // (we want to display the received values), it must do so by synchronizing with the main UI thread;
    // this is achieved by creating a handler within the main thread, which receives messages
    // from another thread and updates UI accordingly;
    // because just received (RX) data affects the main UI, we use the handler only
    // for dealing with received messages, which contain sensor values that need to be
    // used to update the main UI; note that the TX part just sends data, it does not affect
    // anything on the main UI;
    private static class MyStaticHandler extends Handler {
        // using a weak reference means you will not prevent garbage collection, which
        // is the right thing to do;
        private final WeakReference<MainActivity> myMainActivityWeakReference;

        public MyStaticHandler(MainActivity myMainActivityInstance) {
            myMainActivityWeakReference = new WeakReference<MainActivity>(myMainActivityInstance);
        }

        @Override
        public void handleMessage(Message msg) { // msg is received from the thread;
            MainActivity myMainActivity = myMainActivityWeakReference.get();
            if (myMainActivity != null) {
                // do the receiving work here...
                if (msg.what == myMainActivity.handlerState) { // if message is what we want
                    String readMessage = (String) msg.obj; // msg.arg1 = bytes from connect thread
                    myMainActivity.recDataString.append(readMessage); // keep appending to string until >
                    int endOfLineIndex = myMainActivity.recDataString.indexOf(">"); // determine the end-of-line
                    if (endOfLineIndex > 0) { // make sure there is data before >
                        String dataInPrint = myMainActivity.recDataString.substring(1, endOfLineIndex); // extract string;
                        int dataLength = dataInPrint.length(); // get length of data received
                        myMainActivity.txtStringLength.setText( "Length: " + String.valueOf(dataLength));
                        myMainActivity.txtStringReceived.setText( "Data: " + dataInPrint);
                        // clear all string data;
                        myMainActivity.recDataString.delete(0, myMainActivity.recDataString.length());
                    }
                }
            }
        } // handleMessage()
    } // static class MyStaticHandler

    // An example getter to provide it to some external class
    // or just use 'new MyStaticHandler(this)' if you are using it internally.
    // If you only use it internally you might even want it as final member:
    // private final MyStaticHandler my_static_handler = new MyStaticHandler(this);
    public Handler getHandler() {
        return new MyStaticHandler(this);
    }


    // create class for connect thread;
    // now, the reason for working with handlers and threads is that we cannot wait for
    // a bluetooth message to arrive on the main thread of the activity; that may results
    // in an ‘Application Not Responding’ message; to get around this, we can run a new thread
    // for the bluetooth data receiving to take place on, as well as a handler to update
    // the UI when relevant data has been received;
    // more info here:
    // http://www.techotopia.com/index.php/A_Basic_Overview_of_Android_Threads_and_Thread_handlers
    // https://developer.android.com/guide/components/processes-and-threads.html
    // http://www.vogella.com/tutorials/AndroidBackgroundProcessing/article.html
    // TODO:
    // consider using AsyncTask instead of threads and handlers; AsyncTask allows to perform
    // asynchronous work on the user interface; it performs the blocking operations in a worker
    // thread and then publishes the results on the UI thread, without requiring you to handle
    // threads and/or handlers yourself?
    // https://medium.com/@ali.muzaffar/handlerthreads-and-why-you-should-be-using-them-in-your-android-apps-dc8bf1540341
    // TX and RX activities can be handled through the in and out streams of the BlueTooth
    // socket passed here at the time of creation of the thread variable of this type of class;
    private class My_Connected_Thread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        // creation;
        public My_Connected_Thread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                // create I/O streams for connection;
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        // this is the write method;
        // send data, which should be in agreed on format; for example something
        // like *Hello there!* where payload is wrapped by * symbols;
        public void write(String message) {
            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer); // write bytes over BT connection via outstream
            } catch (IOException e) {
                // if transmission fails, tell user that it is most likely because device is not there?
                Toast.makeText(getBaseContext(), "ERROR - Send failed, is device still paired?", Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        // this is basically the read method;
        // look for more info here:
        // https://developer.android.com/reference/java/lang/Thread.html
        public void run() {
            byte[] recBuffer = new byte[256];
            int bytes;
            // keep looping to listen for received messages
            while (true) {
                try {
                    bytes = mmInStream.read(recBuffer); // read bytes from input buffer
                    String readMessage = new String(recBuffer, 0, bytes);
                    // send the received bytes as message to the the static handler, which
                    // will get the message and parse it and then update the main UI;
                    bt_static_handler.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }
    } // class My_Connected_Thread

}
