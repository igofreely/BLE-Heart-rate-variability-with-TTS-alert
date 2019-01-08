package com.sample.hrv.sensor;


import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static java.lang.Math.pow;

/**
 * Created by  by olli on 3/28/2014.
 */
public class BleHeartRateSensor extends BleSensor<float[]> {

    private final static String TAG = BleHeartRateSensor.class.getSimpleName();

    private static final String UUID_SENSOR_BODY_LOCATION = "00002a38-0000-1000-8000-00805f9b34fb";
    
    private static final int SENSOR_BODY_LOCATION_OTHER = 0;
    private static final int SENSOR_BODY_LOCATION_CHEST = 1;
    private static final int SENSOR_BODY_LOCATION_WRIST = 2;
    private static final int SENSOR_BODY_LOCATION_FINGER = 3;
    private static final int SENSOR_BODY_LOCATION_HAND = 4;
    private static final int SENSOR_BODY_LOCATION_EAR = 5;
    private static final int SENSOR_BODY_LOCATION_FOOT = 6;
	public static Context context;
	private int location = -1;
    
	BleHeartRateSensor() {
		super();
	}

	@Override
	public String getName() {
		return "Heart rate";
	}

	@Override
	public String getServiceUUID() {
		return "0000180d-0000-1000-8000-00805f9b34fb";
	}

	public static String getServiceUUIDString() {
		return "0000180d-0000-1000-8000-00805f9b34fb";
	}
	
	@Override
	public String getDataUUID() {
		return "00002a37-0000-1000-8000-00805f9b34fb";
	}

	public static String getDataUUIDString() {
		return "00002a37-0000-1000-8000-00805f9b34fb";
	}
	
	@Override
	public String getConfigUUID() {
		return "00002902-0000-1000-8000-00805f9b34fb";
	}
	
    @Override
    public String getCharacteristicName(String uuid) {
        if (UUID_SENSOR_BODY_LOCATION.equals(uuid))
            return getName() + " Sensor body location";
        return super.getCharacteristicName(uuid);
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGattCharacteristic c) {
        super.onCharacteristicRead(c);

        Log.d(TAG, "onCharacteristicsReas");
        
        if ( !c.getUuid().toString().equals(UUID_SENSOR_BODY_LOCATION) )
            return false;

        location = c.getProperties();
        Log.d(TAG, "Sensor body location: " + location);
        return true;
    }

	@Override
	public String getDataString() {
		final float[] data = getData();
		return "heart rate=" + data[0] + "\ninterval=" + data[1];
	}

	@Override
	public float[] parse(BluetoothGattCharacteristic c) {

		double heartRate = extractHeartRate(c);
		double contact = extractContact(c);
		double energy = extractEnergyExpended(c);
		Integer[] interval = extractBeatToBeatInterval(c);
		
		float[] result = null;
		if (interval != null) {
			result = new float[interval.length + 1];
		} else {
			result = new float[2];
			result[1] = -1.0f;
		}
		result[0] = (float) heartRate;
		
		if (interval != null) {
			for (int i = 0; i < interval.length; i++) {
				result[i+1] = interval[i].floatValue();
			}
		}
		
		return result;
	}

	static TextToSpeech tts;
	static Date startdate;
	static boolean started=false;
	private static double extractHeartRate(
			BluetoothGattCharacteristic characteristic) {

		int flag = characteristic.getProperties();
		Log.d(TAG, "Heart rate flag: " + flag);
		int format = -1;
		// Heart rate bit number format
		if ((flag & 0x01) != 0) {
			format = BluetoothGattCharacteristic.FORMAT_UINT16;
			Log.d(TAG, "Heart rate format UINT16.");
		} else {
			format = BluetoothGattCharacteristic.FORMAT_UINT8;
			Log.d(TAG, "Heart rate format UINT8.");
		}
		final int heartRate = characteristic.getIntValue(format, 1);
		Log.d(TAG, String.format("Received heart rate: %d", heartRate));
		if(!started) {
			startdate = new Date(System.currentTimeMillis());
			started=true;
		}
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");// HH:mm:ss
		//获取当前时间
		Date date = new Date(System.currentTimeMillis());
		long between = date.getTime() - startdate.getTime();
		long day = between / (24 * 60 * 60 * 1000);
		long hour = (between / (60 * 60 * 1000) - day * 24);
		long min = ((between / (60 * 1000)) - day * 24 * 60 - hour * 60);
		long sec = (between / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - min * 60);
		if(between/1000>5)
		{
			Log.d("hrm","心率"+heartRate);
			startdate = new Date(System.currentTimeMillis());
			//playtts(String.valueOf(heartRate));
		}

		return heartRate;
	}
	public static void playtts(final String text){
		tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
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
	private static double extractContact(
			BluetoothGattCharacteristic characteristic) {

		int flag = characteristic.getProperties();
		int format = -1;
		// Sensor contact status
		if ((flag & 0x02) != 0) {
			Log.d(TAG, "Heart rate sensor contact info exists");
			if ((flag & 0x04) != 0) {
				Log.d(TAG, "Heart rate sensor contact is ON");
			} else {
				Log.d(TAG, "Heart rate sensor contact is OFF");
			}
		} else  {
			Log.d(TAG, "Heart rate sensor contact info doesn't exists");
		}
		//final int heartRate = characteristic.getIntValue(format, 1);
		//Log.d(TAG, String.format("Received heart rate: %d", heartRate));
		return 0.0d;
	}
	
	private static double extractEnergyExpended(
			BluetoothGattCharacteristic characteristic) {

		int flag = characteristic.getProperties();
		int format = -1;
		// Energy calculation status
		if ((flag & 0x08) != 0) {
			Log.d(TAG, "Heart rate energy calculation exists.");
		} else {
			Log.d(TAG, "Heart rate energy calculation doesn't exists.");
		}
		//final int heartRate = characteristic.getIntValue(format, 1);
		//Log.d(TAG, String.format("Received heart rate: %d", heartRate));
		return 0.0d;
	}
	
	private static Integer[] extractBeatToBeatInterval(
			BluetoothGattCharacteristic characteristic) {

        int flag = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        int format = -1;
        int energy = -1;
        int offset = 1; // This depends on hear rate value format and if there is energy data
        int rr_count = 0;
        
        if ((flag & 0x01) != 0) {
            format = BluetoothGattCharacteristic.FORMAT_UINT16;
            Log.d(TAG, "Heart rate format UINT16.");
            offset = 3;
        } else {
            format = BluetoothGattCharacteristic.FORMAT_UINT8;
            Log.d(TAG, "Heart rate format UINT8.");
            offset = 2;
        }
        if ((flag & 0x08) != 0) {
            // calories present
            energy = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
            offset += 2;
            Log.d(TAG, "Received energy: {}"+ energy);
        }
        if ((flag & 0x16) != 0){
            // RR stuff.
            Log.d(TAG, "RR stuff found at offset: "+ offset);
            Log.d(TAG, "RR length: "+ (characteristic.getValue()).length);
            rr_count = ((characteristic.getValue()).length - offset) / 2;
            Log.d(TAG, "RR length: "+ (characteristic.getValue()).length);
            Log.d(TAG, "rr_count: "+ rr_count);
			if (rr_count > 0) {
				Integer[] mRr_values = new Integer[rr_count];
				for (int i = 0; i < rr_count; i++) {
					mRr_values[i] = characteristic.getIntValue(
							BluetoothGattCharacteristic.FORMAT_UINT16, offset);
					offset += 2;
					Log.d(TAG, "Received RR: " + mRr_values[i]);
				}
				return mRr_values;
			}
        }
        Log.d(TAG, "No RR data on this update: ");
        return null;
	}



}
