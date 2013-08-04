package edu.ucla.cens.Updater.utils;


import edu.ucla.cens.Updater.AppList;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

public class AppManager {
	private static final String TAG = AppManager.class.getName();

    /**
     * The singleton instance
     */
    private static AppManager instance;
    private Context context;
	
	/**
     * Used for toast alerts in the application.
     */
    private Toast toast;

    // main activity - may or may not be set
	private Activity activity;
	
    public Activity getActivity() {
		return activity;
	}


	public void setActivity(Activity activity) {
		this.activity = activity;
	}
    

    // ******** Public Static functions
    
    public static void create(Context context) {
    	if (instance != null) {
    		Log.w(TAG, "AppManager already created.");
    	} else {
            instance = new AppManager(context);        
    	}
    }

    private AppManager(Context context) {
        this.context = context;
    }
    
    public static synchronized AppManager get() {
        if (instance == null) {
            throw new RuntimeException("Programmig error: AppManager has not been created at startup.");
        }
        return instance;
    }

    public Context getContext() {
        return context;
    }

	
    public void doToastMessage(String message) {
    	Log.d(TAG, message);
    	
        if (toast != null) {
            toast.cancel();
        }
        
        toast = Toast.makeText(context, message,
                Toast.LENGTH_LONG);
        toast.show();
    }
	

    public void doToastMessageAsync(final String message) {
    	activity.runOnUiThread( new Runnable() {
				public void run() {
					doToastMessage(message);
				}
		});
    }
    
    public void nototifyMainActivity() {
    	if (activity != null) {
    		((AppList) activity).notifyDataChanged();
    	}
    	
    }
    
    // ***************** Domain specific public functions ***************
    
    

}
