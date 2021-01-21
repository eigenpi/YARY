package com.eigenpi.btjoystick;

import java.util.Set;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;


// this activity generates a list of the paired BT devices; here, we pick a paired
// device and take its details through to a new activity, where data is sent to the Arduino;


public class BTDeviceListActivity extends Activity {

    // debugging for LOGCAT;
    private static final String TAG = "BTDeviceListActivity";
    // declare textview for connection status
    TextView textview_connecting;
    // EXTRA string to pass on to main activity via an intent;
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_list_layout);
    }

    @Override
    public void onResume() {
        super.onResume();

        // (1) get bluetooth adapter and create list of paired devices;
        mBtAdapter=BluetoothAdapter.getDefaultAdapter();
        check_BT_state();

        // initialize array adapter for paired devices;
        mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name_layout);
        // find and set up the ListView for paired devices;
        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        // get a set of currently paired devices and append to 'pairedDevices'
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        // add previously paired devices to the array;
        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE); // make title viewable
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = getResources().getText(R.string.none_paired).toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }

        // (2) attach to the textview where we display "Connecting..." when
        // user clicks on a device from displayed list;
        textview_connecting = (TextView) findViewById(R.id.connecting_text);
        textview_connecting.setTextSize(40);
        textview_connecting.setText(" ");
    }

    // on-item-click listener for the list;
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            textview_connecting.setText("Connecting...");
            // get the BT device MAC address, which is the last 17 chars in the text view
            String info = ((TextView) v).getText().toString();
            String mac_address = info.substring(info.length() - 17);

            // create an intent to start next activity, MainActivity, while taking
            // an extra, which is the MAC address;
            Intent i = new Intent(BTDeviceListActivity.this, MainActivity.class);
            i.putExtra(EXTRA_DEVICE_ADDRESS, mac_address);
            startActivity(i);
        }
    };

    // check if the Android BT device is enabled/available and prompts user to turned
    // it ON if it is not so;
    private void check_BT_state() {
        if(mBtAdapter == null) {
            Toast.makeText(getBaseContext(), "Device does not support Bluetooth", Toast.LENGTH_SHORT).show();
        } else {
            if (mBtAdapter.isEnabled()) {
                Log.d(TAG, "Bluetooth is ON");
            } else {
                // prompt user to turn on Bluetooth;
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

}
