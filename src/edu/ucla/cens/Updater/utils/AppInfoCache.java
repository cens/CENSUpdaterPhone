package edu.ucla.cens.Updater.utils;

import java.util.HashMap;

import edu.ucla.cens.Updater.model.AppInfoModel;

/**
 * Maintains list of applications with installation details.
 * This class is a singleton.
 */
public class AppInfoCache extends HashMap<String, AppInfoModel> {

	//private Map<String, AppInfoModel> appMap = new HashMap<String, AppInfoModel>();
	private static final long serialVersionUID = 1L;
	
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
	
	public void add(AppInfoModel app) {
		put(app.getQualifiedName(), app);
	}
	
}
