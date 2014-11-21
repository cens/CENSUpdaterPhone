package org.nexleaf.shared;

import edu.ucla.cens.Updater.utils.AppManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.telephony.TelephonyManager;
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
     * Queries current radio mode.
     * @return true if radio is on, false otherwise
     */
    public static boolean isRadioOn() {
        TelephonyManager telMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        int networkType = telMgr.getNetworkType();
        // 0: NETWORK_TYPE_UNKNOWN
        // 
        // 1: GPRS
        // 2: int  NETWORK_TYPE_EDGE    Current network is EDGE
        boolean rc = (networkType != TelephonyManager.NETWORK_TYPE_UNKNOWN);
        Log.d(TAG, "isRadioOn: networkType="+networkType+"; returning " + rc);
        return rc;
    }
        
    /**
     * Sends a request for radio to be turned on.
     * The request is sent even if the state already seems to be as requested.
     * @param sender: application name of the app that is sending request
     * @param useCase: use case id. e.g. 1-1 for rebooter sending Test/Ping SMS
     * @return true if radio appears to be already in the requested state, false
     *   otherwise
     */
    public static boolean requestRadioOn(String sender, String useCase) {
        boolean currentState = isRadioOn();
        Intent intent = new Intent();
        intent.setAction(SharedConstants.ACTION_RADIO_ON);
        intent.putExtra("sender", sender);
        intent.putExtra("useCase", useCase);
        Log.d(TAG, "requestRadioOn: ACTION_RADIO_ON " + sender + ", " + useCase);
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
