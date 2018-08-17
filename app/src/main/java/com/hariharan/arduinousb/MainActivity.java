package com.hariharan.arduinousb;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import java.net.URL;
import android.os.AsyncTask;
import java.io.*;
import org.json.*;
import com.loopj.android.http.*;
import android.os.Handler;

import cz.msebera.android.httpclient.Header;

public class MainActivity extends Activity {
    public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";
    Button startButton, sendButton, clearButton, stopButton;
    TextView textView;
    TextView arduino_alive;
    TextView rally_id;
    TextView rally_name;
    TextView connection_status;
    TextView terminal;
    EditText editText;
    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;
    String rallyId = "";
    String rallyName = "";

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;
            try {
                data = new String(arg0, "UTF-8");
                //data.concat("/n");

                data = data.replace("\n", "");
                data = data.replace("\r", "");

                if (!data.equals("")) {
                    if (data.length() > 1 && data.substring(0, 1).equals("^")) {
                        statusAlive(data);
                    } else {
                        SendToServer2(data);
                    }
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }


        }
    };

    private final void SendToServer(String text) {
        //tvAppend(textView,"Sending to server\n");

        String term = terminal.getText().toString();
        text = "T" + term + "-" + text;

        tvAppend(textView, "\n"+text);

        AsyncHttpClient client = new AsyncHttpClient();
        client.get("https://gonki.in.ua/rallies/404/site/androidinput?data="+text, new AsyncHttpResponseHandler() {

            @Override
            public void onStart() {
                // called before request is started
                //tvAppend(textView, "\nGET");
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                // called when response HTTP status is "200 OK"
                //tvAppend(textView, "-->GET 200");
                CharSequence seq = new String(response);
                tvAppend(textView, seq);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                tvAppend(textView, "\nERROR: "+statusCode);
                CharSequence seq = new String(errorResponse);
                tvAppend(textView, seq);
            }

            @Override
            public void onRetry(int retryNo) {
                // called when request is retried
                tvAppend(textView, "\nGET RETRY");
            }
        });

    }

    private final void SendToServer2(String text) {
        //tvAppend(textView,"Sending to server\n");

        String term = terminal.getText().toString();
        text = "T" + term + "-" + text;

        tvAppend(textView, "\n"+text);

        SyncHttpClient client = new SyncHttpClient();
        client.get("https://gonki.in.ua/rallies/404/site/androidinput?data="+text, new JsonHttpResponseHandler() {
            @Override
            public void onStart() {
                //tvAppend(textView, "\nSEND: ");
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                //tvAppend(textView, " - on server");
                CharSequence seq = new String(response.toString());
                tvAppend(textView, seq);
            }


            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable e, JSONObject errorResponse) {
                tvAppend(textView, "\nERROR: "+statusCode);
                CharSequence seq = new String(errorResponse.toString());
                tvAppend(textView, seq);
            }

        });
    }

    private final void ServerInfo() {

        AsyncHttpClient client = new AsyncHttpClient();
        client.get("https://gonki.in.ua/?act=getRallyInfo", new AsyncHttpResponseHandler() {

            @Override
            public void onStart() {
                // called before request is started
                //tvAppend(textView, "GET");
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                // called when response HTTP status is "200 OK"
                //tvAppend(textView, "200");
                //CharSequence seq = new String(response);
                //tvAppend(textView, seq);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connection_status.setText("Server ON");
                    }
                });

                String s = new String(response);
                try {
                    JSONObject data = new JSONObject(s);
                    //tvAppend(textView, data.getString("id"));
                    rallyId = data.getString("id");
                    rallyName = data.getString("name");
                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                tvAppend(textView, "ERROR: "+statusCode);
                if (errorResponse != null) {
                    CharSequence seq = new String(errorResponse);
                    tvAppend(textView, seq);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connection_status.setText("Server OFF");
                    }
                });
            }

            @Override
            public void onRetry(int retryNo) {
                // called when request is retried
                tvAppend(textView, "RETRY");
            }
        });

    }


    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            setUiEnabled(true);
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback);
                            tvAppend(textView,"Serial Connection Opened!\n");

                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                onClickStart(startButton);
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                onClickStop(stopButton);

            }
        }

        ;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);
        startButton = (Button) findViewById(R.id.buttonStart);
        sendButton = (Button) findViewById(R.id.buttonSend);
        clearButton = (Button) findViewById(R.id.buttonClear);
        stopButton = (Button) findViewById(R.id.buttonStop);
        editText = (EditText) findViewById(R.id.editText);
        textView = (TextView) findViewById(R.id.textView);
        arduino_alive = (TextView) findViewById(R.id.arduino_alive);
        rally_id = (TextView) findViewById(R.id.rally_id);
        rally_name = (TextView) findViewById(R.id.rally_name);
        connection_status = (TextView) findViewById(R.id.connection_status);
        terminal = (TextView) findViewById(R.id.terminal);
        setUiEnabled(false);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);

        // Create the Handler object (on the main thread by default)
        final Handler handler = new Handler();
        // Define the code block to be executed
        Runnable runnableCode = new Runnable() {
            @Override
            public void run() {
                ServerInfo();

                //tvAppend(textView, "timeout");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rally_id.setText(rallyId);
                        rally_name.setText(rallyName);
                    }
                });
                handler.postDelayed(this, 10000);
            }
        };
        // Start the initial runnable task by posting through the handler
        handler.post(runnableCode);

    }

    public void setUiEnabled(boolean bool) {
        startButton.setEnabled(!bool);
        sendButton.setEnabled(true);
        stopButton.setEnabled(bool);
        textView.setEnabled(bool);
        arduino_alive.setEnabled(bool);
        rally_id.setEnabled(bool);
        rally_name.setEnabled(bool);
        connection_status.setEnabled(bool);
        terminal.setEnabled(true);

    }

    public void onClickStart(View view) {


        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (true || deviceVID == 0x2341)//Arduino Vendor ID
                {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }


    }

    public void onClickSend(View view) {
        String string = editText.getText().toString();
        editText.setText("");
        //serialPort.write(string.getBytes());
        //tvAppend(textView, "\nData Sent : " + string + "\n");
        SendToServer(string);
        tvAppend(textView, " - hand");

    }

    public void onClickStop(View view) {
        setUiEnabled(false);
        serialPort.close();
        tvAppend(textView,"\nSerial Connection Closed! \n");

    }

    public void onClickClear(View view) {
        textView.setText("");
        SendToServer("test111");
    }

    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ftv.append(ftext);
            }
        });
    }

    private void statusAlive(CharSequence text) {
        final CharSequence ftext = text;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                arduino_alive.setText(ftext);
            }
        });
    }

}
