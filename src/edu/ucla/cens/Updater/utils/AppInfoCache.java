package edu.ucla.cens.Updater.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;
import edu.ucla.cens.Updater.model.AppInfoModel;
import edu.ucla.cens.Updater.model.SettingsModel;

/**
 * Maintains list of applications with installation details.
 * This class is a singleton.
 */
public class AppInfoCache extends HashMap<String, AppInfoModel> {
    public static final String TAG = AppInfoCache.class.getSimpleName();

	//private Map<String, AppInfoModel> appMap = new HashMap<String, AppInfoModel>();
	private static final long serialVersionUID = 1L;
	private List<String> idList = new ArrayList<String>();
	private String dataRetrievalError = null;
	public String getDataRetrievalError() {
		return dataRetrievalError;
	}

	public void setDataRetrievalError(String dataRetrievalError) {
		this.dataRetrievalError = dataRetrievalError;
	}

	/**
	 * Singleton instance
	 */
	private static AppInfoCache instance;
	
	private AppInfoCache() {
	}
	
	/**
	 * Retrieves singleton instance.
	 * @return the instance
	 */
	public static synchronized AppInfoCache get() {
		if (instance == null) {
			instance = new AppInfoCache();
		}
		return instance;
	}
	
	public void clear() {
		super.clear();
		idList.clear();
	}
	public void add(AppInfoModel app) {
		put(app.getQualifiedName(), app);
		idList.add(app.getQualifiedName());
	}

	public AppInfoModel getItemAt(int position) {
		if (position >= idList.size() || position < 0) {
			throw new IndexOutOfBoundsException();
		}
		return get(idList.get(position));
	}

	public void resetDataRetrievalError() {
		dataRetrievalError = null;
	}

	public boolean hasDataRetrievalError() {
		return dataRetrievalError != null;
	}

	/**
	 * Load cache from persistent store
	 */
	public void load() {
		// TODO: implement save
		// perhaps use JSONBeans: https://code.google.com/p/jsonbeans
		String appInfoJsonString = SettingsModel.get().getPrefsString("appInfo");
		try {
			JSONObject appInfo = new JSONObject(appInfoJsonString);
			// ...
		} catch (JSONException e) {
			Log.e(TAG, "Can't load appInfo: " + e);
			throw new RuntimeException(e);
		}
	}
	
	public void save() {
		// TODO: implement load
		//for (AppInfoModel appInfo: values()) {
		//	appInfo.
		//}
	}
	
	
}
