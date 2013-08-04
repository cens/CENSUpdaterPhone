package edu.ucla.cens.Updater.utils;

import java.util.Locale;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import edu.ucla.cens.Updater.UpdateReceiver;
import edu.ucla.cens.Updater.model.SettingsModel;
import edu.ucla.cens.systemlog.Log;

/**
 * Holds utility functions.
 */
public class Utils {
	
	private static final String TAG = Utils.class.getName();
	
	private static Context context = AppManager.get().getContext();
	private static String deviceId = queryDeviceId();


	public static void resetAlarm() {
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		
		SettingsModel model = SettingsModel.get();
		long sleepPeriod = model.getUpdateFrequencyMillis();
		Log.i(TAG, "resetAlarm: sleepPeriod=" + sleepPeriod);
		Intent intentToFire = new Intent(UpdateReceiver.UPDATE_ACTION);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intentToFire, PendingIntent.FLAG_CANCEL_CURRENT);
 		alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), sleepPeriod, pendingIntent);
		Log.i(TAG, "resetAlarm: set");
		
	}

	/**
	 * Improves MAC address string by removing colons and making it uppercase.
	 * @param s
	 * @return
	 */
	private static String improveMac(String s) {
		return s.replaceAll(":", "").toUpperCase(Locale.US);
	}
	/**
	 * Queries device id from the device.
	 * The device id is IMEI if there is a SIM card,
	 * or Wifi MAC address if not, or Bluetooth Mac address
	 * if there is no Wifi.
	 */
	
	private static String queryDeviceId() {
		String id = null;
		TelephonyManager telephonyManager = ((TelephonyManager) 
				context.getSystemService(Context.TELEPHONY_SERVICE));
		if (telephonyManager != null) {
			id = telephonyManager.getDeviceId();
		}
		if (id == null) {
			WifiManager wifiMan = (WifiManager) context.getSystemService(
	                Context.WIFI_SERVICE);
			if (wifiMan != null) {
				id = improveMac(wifiMan.getConnectionInfo().getMacAddress());
			}
		}
		if (id == null) {
			BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
			if (btAdapter != null) {
				id = improveMac(btAdapter.getAddress());				
			}
		}
		return id;
	}

	/**
	 * Returns a device id.
	 */
	public static String getDeviceId() {
		return deviceId;
	}
	
}
