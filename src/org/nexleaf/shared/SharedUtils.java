package org.nexleaf.shared;

import edu.ucla.cens.Updater.utils.AppManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * Shared utility functions, shared across all nexleaf projects.
 * Note: this class has to be instantiated after AppManager of this project has 
 * been instantiated first.
 */
public class SharedUtils {
    public static final String TAG = SharedUtils.class.getSimpleName();
    
    /**
     * Context to be used for operations such as getAssets
     */
    private static Context context;
    
    // init
    static {
        context = AppManager.get().getContext();        
    }

    /**
     * Queries current airplane mode.
     * @return true if airplane mode is on, false otherwise
     */
    public static boolean isAirplaneModeOn() {
        return Settings.System.getInt(context.getContentResolver
                                (),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }
    
    /**
     * Sends a request for radio to be turned on (airplane mode off).
     * The request is sent even if the state already seems to be as requested.
     * @return true if radio seems to be already in the requested state, false
     *   otherwise
     */
    public static boolean requestRadioOn() {
        boolean currentState = !isAirplaneModeOn();
        Intent intent = new Intent();
        intent.setAction(SharedConstants.ACTION_RADIO_ON);
        Log.d(TAG, "requestRadioOn: sendBroadcast intent: ACTION_RADIO_ON");
        context.sendBroadcast(intent);
        return currentState;
    }

    /**
     * Wait until we think the radio is on.
     * This is approximate and will wait until SendReceiver service that controls
     * airplane mode etc. has had a chance to set radio on. 
     */
    public static void waitForRadioOn() {
        try {
            Thread.sleep(SharedConstants.DEFAULT_WAIT_RADIO_ON);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
    }

    
}
