package edu.ucla.cens.Updater.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;


/**
 * Model for status of updates.
 */
public class StatusModel extends SimpleObservable<StatusModel> {
    public static final String TAG = StatusModel.class.getSimpleName();

	/**
	 * Singleton instance
	 */
	private static StatusModel instance;

	private Map<String, String> packageUpdates = new HashMap<String, String>();
	
	private String lastCheckTs = null;
	private String lastCheckMessage = "";
	private String lastErrorTs = null;
	private String lastErrorMessage = "";

	/**
	 * Retrieves singleton instance.
	 * @return the instance
	 */
	public static synchronized StatusModel get() {
		if (instance == null) {
			instance = new StatusModel();
		}
		return instance;
	}
	
	/**
	 * Saves to preferences
	 */
	public void save() {
		
	}
	
	/**
	 * Loads from preferences
	 */
	public void load() {
		
	}
	
	public void addPackageInfo(String name, String date) {
		packageUpdates.put(name, date);
	}

	public void addInfoMessage(String message) {
		lastCheckTs = new Date().toString();
		lastCheckMessage = message;
		Log.d(TAG, this + " addInfoMessage: " + lastCheckTs + ": " + lastCheckMessage);
		notifyObservers(this);
	}
	public void addErrorMessage(String message) {
		lastErrorTs = new Date().toString();
		lastErrorMessage = message;
		Log.e(TAG, this + " addErrorMessage: " + lastErrorTs + ": " + lastErrorMessage);
		notifyObservers(this);
	}

	public void dump() {
		Log.d(TAG, this + " dump: " + lastCheckTs + ": " + lastCheckMessage);
	}

	public Map<String, String> getPackageUpdates() {
		return packageUpdates;
	}

	public String getLastCheckTs() {
		return lastCheckTs;
	}

	public String getLastCheckMessage() {
		return lastCheckMessage;
	}

	public String getLastErrorTs() {
		return lastErrorTs;
	}

	public String getLastErrorMessage() {
		return lastErrorMessage;
	}
	
}
