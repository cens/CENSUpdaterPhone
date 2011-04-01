package edu.ucla.cens.Updater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import edu.ucla.cens.systemlog.Log;

/**
 * Custom preference Activity that displays the preferences and, when closed,
 * resets the automatic update alarms.
 * 
 * @author John Jenkins
 * @version 1.0
 */
public class CustomPreferenceActivity extends PreferenceActivity
{
	private static final String TAG = "CENS.Updater.CustomPreferenceActivity";
	
	/**
	 * Creates and loads the preferences view.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.preferences);
		
		Log.initialize(this, Database.LOGGER_APP_NAME);
	}
	
	/**
	 * "Commits" the changes by canceling the current update trigger and
	 * starting the new cycle based on the frequency the user input.
	 */
	@Override
	protected void onPause()
	{
		super.onPause();
		
		Log.v(TAG, "Canceling the current update schedule and scheduling a new one.");
		
		AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		alarmManager.set(AlarmManager.RTC_WAKEUP, 
				System.currentTimeMillis(), 
				PendingIntent.getBroadcast(this, 0, new Intent(UpdateReceiver.RESET_ACTION), 0));
	}
}