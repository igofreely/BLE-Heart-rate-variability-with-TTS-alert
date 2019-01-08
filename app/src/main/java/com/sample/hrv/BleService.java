/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sample.hrv;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.Inflater;

import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import com.sample.hrv.R;

import com.sample.hrv.sensor.BleSensor;
import com.sample.hrv.sensor.BleSensors;

import android.util.Log;
/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BleService extends Service {
    private final static String TAG = BleService.class.getSimpleName();

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter adapter;
    private String deviceAddress;
    private BluetoothGatt gatt;
    private int connectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private final static String INTENT_PREFIX = BleService.class.getPackage().getName();
    public final static String ACTION_GATT_CONNECTED = INTENT_PREFIX+".ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = INTENT_PREFIX+".ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = INTENT_PREFIX+".ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = INTENT_PREFIX+".ACTION_DATA_AVAILABLE";
    public final static String EXTRA_SERVICE_UUID = INTENT_PREFIX+".EXTRA_SERVICE_UUID";
    public final static String EXTRA_CHARACTERISTIC_UUID = INTENT_PREFIX+".EXTRA_CHARACTERISTIC_UUI";
    public final static String EXTRA_DATA = INTENT_PREFIX+".EXTRA_DATA";
    public final static String EXTRA_TEXT = INTENT_PREFIX+".EXTRA_TEXT";

    public static String editedtext="";
//    private Spinner mSp1;
//    private String [] logmethod;
//    private ArrayAdapter<String> adapter2;
//    private TextView mTv1;
//    private LayoutInflater inflater;
//    private View view;
//    public class spinnerListener implements android.widget.AdapterView.OnItemSelectedListener{
//        @Override
//        public void onItemSelected(AdapterView<?> parent, View view,
//                                   int position, long id) {
//            //将选择的元素显示出来
//            String selected = parent.getItemAtPosition(position).toString();
//            Log.d("心率","已选择"+selected);
//            Toast.makeText(getApplicationContext(),selected, Toast.LENGTH_LONG).show();
//        }
//        @Override
//        public void onNothingSelected(AdapterView<?> parent) {
//            Log.d("心率","无选择");
//            System.out.println("nothingSelect");
//        }
//    }
    // Implements callback methods for GATT events that the app cares about.
    // For example, connection change and services discovered.
    private final BluetoothGattExecutor executor = new BluetoothGattExecutor() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                connectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        BleService.this.gatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                connectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                final BleSensor<?> sensor = BleSensors.getSensor(characteristic.getService().getUuid().toString());
                if (sensor != null) {
                    if (sensor.onCharacteristicRead(characteristic)) {
                        return;
                    }
                }

                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }
    int heartratezone=0;
    Date startdate,startdate2;
    boolean started=false;
    private TextToSpeech tts;
    public void playtts(final String text){
        //Toast.makeText(getApplicationContext(),"这是弹窗",Toast.LENGTH_SHORT).show();
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                //初始化成功的话，设置语音
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.CHINESE);
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                }
            }
        });
    }
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_SERVICE_UUID, characteristic.getService().getUuid().toString());
        intent.putExtra(EXTRA_CHARACTERISTIC_UUID, characteristic.getUuid().toString());

        final BleSensor<?> sensor = BleSensors.getSensor(characteristic.getService().getUuid().toString());
        if (sensor != null) {
            sensor.onCharacteristicChanged(characteristic);
            final String text = sensor.getDataString();
            intent.putExtra(EXTRA_TEXT, text);
            String result=text;
            Log.d("111hrm心率","aaa心率"+result);
            if(!started) {
                startdate = new Date(System.currentTimeMillis());
                startdate2 = new Date(System.currentTimeMillis());
                started=true;
            }
//            LayoutInflater inflater;
//            View view;
//            inflater = LayoutInflater.from(getApplicationContext());
//            view = inflater.inflate(R.layout.gatt_services_characteristics, null);
//            EditText tv=(EditText)view.findViewById(R.id.editView001);
            String[] para=editedtext.split("\\ ");
            int max=0,low=0,high=0;
            if (para.length==3&&Integer.parseInt(para[0]) >=180 &&Integer.parseInt(para[0]) <= 200 &&Integer.parseInt(para[1])<Integer.parseInt(para[2])&&Integer.parseInt(para[1]) >=50 &&Integer.parseInt(para[2]) <= 100) {
                max=Integer.parseInt(para[0]);
                low=Integer.parseInt(para[1]);
                high=Integer.parseInt(para[2]);
            }
            if(result.contains("heart rate=")&&result.length()>24) {
                String s = result.split("\\=")[1].split("\\.")[0];
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");// HH:mm:ss
                //获取当前时间
                Date date = new Date(System.currentTimeMillis());
                long between = date.getTime() - startdate.getTime();
                long between2 = date.getTime() - startdate2.getTime();
                long day = between / (24 * 60 * 60 * 1000);
                long hour = (between / (60 * 60 * 1000) - day * 24);
                long min = ((between / (60 * 1000)) - day * 24 * 60 - hour * 60);
                long sec = (between / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - min * 60);
                if (between/1000>30)//
                {
                    playtts("心率" + s);
                    startdate = new Date(System.currentTimeMillis());
                }
                if (between2/1000>5)//
                {
                    if (max == 0 || low == 0 || high == 0) {
                        int i = Integer.parseInt(s);
                        int currentzone=i*10/190-2;
                        if(currentzone!=heartratezone)
                        {
                            heartratezone=i*10/190-2;
                            playtts("心率区间"+currentzone);
                        }
                       } else {
                        int i = Integer.parseInt(s);
                        if (i < max * low / 100)
                            playtts("心率过低");
                        else if (i > max * high / 100)
                            playtts("心率过高");
                    }
                    startdate2 = new Date(System.currentTimeMillis());
                }
            }
            sendBroadcast(intent);
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_TEXT, new String(data) + "\n" + stringBuilder.toString());
                //String s=EXTRA_TEXT.split("\\=")[1].split("\\.")[0];
                Log.d("111hrm心率","bbb心率"+EXTRA_TEXT);
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    /**
	 * Enables or disables notification on a give characteristic.
	 *
	 * @param sensor
	 * @param enabled If true, enable notification.  False otherwise.
	 */
	public void enableSensor(BleSensor<?> sensor, boolean enabled) {
	    if (sensor == null)
	        return;
	
	    if (adapter == null || gatt == null) {
	        Log.w(TAG, "BluetoothAdapter not initialized");
	        return;
	    }
	
	    executor.enable(sensor, enabled);
	    executor.execute(gatt);
	}

	private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        adapter = bluetoothManager.getAdapter();
        if (adapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
//        inflater = LayoutInflater.from(getApplicationContext());
//        view = inflater.inflate(R.layout.gatt_services_characteristics, null);
//        mSp1 = (Spinner)view.findViewById(R.id.spinner);
//        //准备要加载的字符串数组资源
//        logmethod = getResources().getStringArray(R.array.heartratezone);
//        adapter2 = new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_spinner_item,logmethod);
//        Log.d("心率",logmethod.toString());
//        mSp1.setAdapter(adapter2);
//        mSp1.setOnItemSelectedListener(new spinnerListener());
        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (adapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (deviceAddress != null && address.equals(deviceAddress)
                && gatt != null) {
            Log.d(TAG, "Trying to use an existing BluetoothGatt for connection.");
            if (gatt.connect()) {
                connectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = adapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        gatt = device.connectGatt(this, false, executor);
        Log.d(TAG, "Trying to create a new connection.");
        deviceAddress = address;
        connectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (adapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        gatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (gatt == null) {
            return;
        }
        gatt.close();
        gatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (adapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        gatt.readCharacteristic(characteristic);
    }

    public void updateSensor(BleSensor<?> sensor) {
        if (sensor == null)
            return;

        if (adapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        executor.update(sensor);
        executor.execute(gatt);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (gatt == null) return null;

        return gatt.getServices();
    }
}
