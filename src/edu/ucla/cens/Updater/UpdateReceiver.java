package edu.ucla.cens.Updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import edu.ucla.cens.Updater.utils.AppManager;
import edu.ucla.cens.Updater.utils.Utils;
import edu.ucla.cens.systemlog.Log;

/**
 * Performs periodic update based on scheduled alarms and schedules the alarm
 * after reboot.
 * 
 */
public class UpdateReceiver extends BroadcastReceiver
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

	/**
	 * Last time Updater was run.
	 */
	private static long lastRun = 0;

	/**
	 * Minimum frequency of updater execution, in ms.
	 * It represents a throttle on updater execution.
	 */
	private long frequencyThreshold = 30000;
	
	/**
	 * Receives the Actions sent to the Updater.
	 */
	@Override
	public void onReceive(Context context, Intent intent)
	{
		AppManager.create(context);
		String action = intent.getAction();
		if(action.equals(Intent.ACTION_BOOT_COMPLETED))
		{
			Log.i(TAG, "Setting alarm after boot");
			Utils.resetAlarm();
		}
		else if(action.equals(UPDATE_ACTION))
		{
			Log.i(TAG, "Performing update in " + this);
			mContext = context;
			// Check for updates in a new thread.
			long now = System.currentTimeMillis();
			long delta = now  - lastRun;
			if (delta < frequencyThreshold ) {
				Log.e(TAG, "Can't start Updater: an Updater has run " + delta + " ms ago which is < " + frequencyThreshold);
			} else {
				Log.d(TAG, "Last Updater run was " + delta + " ms ago. Running again now.");
				doUpdate();
			}
		}
	}
	
	/**
	 * Hold a WakeLock while you check for updates. If updates are available,
	 * add a Notification to the NotificationBar.
	 */
	public void doUpdate()
	{
		lastRun = System.currentTimeMillis();
		try {
			mWakeLock = ((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
			mWakeLock.acquire();
			Log.i(TAG, "Aquired wake lock.");
			Updater updater = new Updater(mContext);
			updater.doUpdate();
		}
		catch(NullPointerException e) {
			Log.e(TAG, "Failed to acquire wake lock, or updater failed: ", e);
		}
		finally
		{
			mWakeLock.release();
			Log.i(TAG, "Released wake lock.");
		}
	}
}
