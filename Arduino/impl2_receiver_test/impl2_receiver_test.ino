// cris: I changed it from working with the RF wireless module
// of the original implemnetation, to working with Bluetoth HC-05 module

#include <SoftwareSerial.h>

#define DEBUG true

// connect the TX of BT module to Arduino pin 2 RX;
// connect the RX of BT module to Arduino pin 3 TX;

SoftwareSerial bt_SW_serial(2, 3); // RX | TX

void setup()
{
  Serial.begin(9600);

  bt_SW_serial.begin(9600); 
  Serial.println("Waiting to receive data from Bluetooth module...");
  
  delay(1000);
}

void loop() 
{ 
  String received_data = "";
  String echoed_back_message = "<";
  int counter = 0;
  
  while ( bt_SW_serial.available()) {      
    // bt module has data;
    char c = bt_SW_serial.read(); // read the next character
    received_data += c;
    counter ++;
  }  
  if (counter > 0) {
    // (1) got something;
    Serial.print( received_data);
    Serial.print( "\n");
    // (2) send it back to double check it inside Android app;
    // echo it back; will be received by Android app;
    // but, the App understands it only if wrapped with < >
    echoed_back_message += received_data;
    echoed_back_message += ">"; 
    bt_SW_serial.print( echoed_back_message);
  }
    
  delay(1000); 
}
