package edu.ucla.cens.Updater;

import java.util.Timer;
import java.util.TimerTask;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import edu.ucla.cens.Updater.model.SettingsModel;
import edu.ucla.cens.Updater.utils.Utils;
import edu.ucla.cens.systemlog.Log;

/**
 * Custom preference Activity that displays the preferences and, when closed,
 * resets the automatic update alarms.
 * 
 */
public class CustomPreferenceActivity extends PreferenceActivity {

	private static final String TAG = "CENS.Updater.CustomPreferenceActivity";

	/**
	 * Creates and loads the preferences view.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
        getPreferenceManager().setSharedPreferencesName(SettingsModel.SHARED_PREFERENCES_NAME);
		addPreferencesFromResource(R.xml.preferences);
		
		//Log.initialize(this, Database.LOGGER_APP_NAME);
		Preference pref = findPreference("version_preference");
		ApplicationInfo applicationInfo = getApplicationInfo();
		String version = "unknown";
		String appname = "unknown";
		try {
			PackageManager packageManager = getPackageManager();
			PackageInfo packageInfo = packageManager.getPackageInfo(
					applicationInfo.packageName, 0);
			version = packageInfo.versionName;
			appname =  packageManager.getApplicationLabel(applicationInfo).toString();
		} catch (NameNotFoundException e) {
			// not found, use "unknown" version
		}
		pref.setTitle(appname + " v" + version);
        pref = findPreference("updateFrequency");
        pref.setOnPreferenceChangeListener(prefChange);

	}

	OnPreferenceChangeListener prefChange = new OnPreferenceChangeListener() {
		

		public boolean onPreferenceChange(Preference prefs, Object ovalue) {
			Log.i(TAG, "Preference has been changed: "+prefs.getKey()+", new value: "+ovalue);
			
			String key = prefs.getKey();
			if(key.equals("updateFrequency")) { 
				Log.i(TAG, "updateFrequency changed to " + ovalue);
				// run resetAlarm later after this notification returned
				final Timer timer = new Timer();
				timer.schedule(new TimerTask() {
				    public void run() {
						Utils.resetAlarm();
				        timer.cancel();
				    }
				}, 1000);
				
			}
			return true;
		}
	};	


}