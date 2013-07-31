package edu.ucla.cens.Updater.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import edu.ucla.cens.Updater.UpdateReceiver;
import edu.ucla.cens.Updater.model.SettingsModel;
import edu.ucla.cens.systemlog.Log;

/**
 * Holds utility functions.
 */
public class Utils {
	
	private static final String TAG = Utils.class.getName();


	public static void resetAlarm() {
		Context context = AppManager.get().getContext();
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		
		SettingsModel model = SettingsModel.get();
		long sleepPeriod = model.getUpdateFrequencyMillis();
		Log.i(TAG, "resetAlarm: sleepPeriod=" + sleepPeriod);
		Intent intentToFire = new Intent(UpdateReceiver.UPDATE_ACTION);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intentToFire, PendingIntent.FLAG_CANCEL_CURRENT);
 		alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), sleepPeriod, pendingIntent);
		Log.i(TAG, "resetAlarm: set");
		
	}
	
}
