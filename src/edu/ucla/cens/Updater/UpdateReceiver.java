package edu.ucla.cens.Updater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import edu.ucla.cens.systemlog.Log;

/**
 * Basic receiver that catches the boot call and creates its own recurring
 * broadcast. The broadcasts are either: do an update, which spawns a new
 * update thread and sets a recurring background update Action; or reset the
 * broadcast, which cancels the current recurring background update Action and
 * creates a new one. The latter is primarily called after the user updates
 * the automatic update frequency.
 *  
 * @author John Jenkins
 * @version 1.0
 */
public class UpdateReceiver extends BroadcastReceiver implements Runnable
{
	private static final String TAG = "CENS.UpdateReceiver";
	
	/**
	 * The Action that is ticked when an automatic update occurs.
	 */
	public static final String UPDATE_ACTION = "edu.ucla.cens.Updater.Update";
	/**
	 * The Action that will cancel the current alarm and create a new one.
	 * This is generally called after the update frequency has changed.
	 */
	public static final String RESET_ACTION = "edu.ucla.cens.Updater.Reset";
	
	private WakeLock mWakeLock;
	private Context mContext;
	
	private static PendingIntent mPendingIntent = null;
	
	/**
	 * Receives the Actions sent to the Updater.
	 */
	@Override
	public void onReceive(Context context, Intent intent)
	{
		if(mPendingIntent == null)
		{
			mPendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(UPDATE_ACTION), 0);
		}
		
		Log.initialize(context, Database.LOGGER_APP_NAME);
		
		String action = intent.getAction();
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		if(action.equals(Intent.ACTION_BOOT_COMPLETED) || action.equals(UPDATE_ACTION))
		{
			mContext = context;
			
			// Check for updates in a new thread.
			(new Thread(this)).start();
			
			// Set the check-for-updates Alarm.
			FrequencyPreference fp = new FrequencyPreference(context);
			long updateFrequencyInMillis = fp.getUpdateFrequencyInMillis();
			
			Log.i(TAG, "Setting an alarm for " + updateFrequencyInMillis + " milliseconds from now.");
			alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + updateFrequencyInMillis, mPendingIntent);
		}
		else if(action.equals(RESET_ACTION))
		{
			Log.i(TAG, "Canceling the old pending intent.");
			alarmManager.cancel(mPendingIntent);
			
			FrequencyPreference fp = new FrequencyPreference(context);
			long updateFrequencyInMillis = fp.getUpdateFrequencyInMillis();
			
			Log.i(TAG, "Setting an alarm for " + updateFrequencyInMillis + " milliseconds from now.");
			alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + updateFrequencyInMillis, mPendingIntent);
		}
	}
	
	/**
	 * Hold a WakeLock while you check for updates. If updates are available,
	 * add a Notification to the NotificationBar.
	 */
	@Override
	public void run()
	{
		try
		{
			mWakeLock = ((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
			mWakeLock.acquire();
			
			Log.i(TAG, "Aquired wake lock.");
		}
		catch(NullPointerException e)
		{
			Log.e(TAG, "Failed to acquire wake lock.", e);
			return;
		}
		
		try
		{
			Updater updater = new Updater(mContext);
			updater.doUpdate();
		}
		finally
		{
			mWakeLock.release();
			
			Log.i(TAG, "Released wake lock.");
		}
	}
}
