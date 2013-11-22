package edu.ucla.cens.Updater;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URLEncoder;
import java.security.InvalidParameterException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.SQLException;
import android.net.ConnectivityManager;
import android.os.IBinder;
import edu.ucla.cens.Updater.PackageInformation.Action;
import edu.ucla.cens.Updater.model.AppInfoModel;
import edu.ucla.cens.Updater.model.SettingsModel;
import edu.ucla.cens.Updater.model.StatusModel;
import edu.ucla.cens.Updater.utils.AppInfoCache;
import edu.ucla.cens.Updater.utils.Constants;
import edu.ucla.cens.Updater.utils.RestClient;
import edu.ucla.cens.Updater.utils.ServiceClientException;
import edu.ucla.cens.Updater.utils.Utils;
import edu.ucla.cens.systemlog.Log;

import android.provider.Settings;
import android.telephony.TelephonyManager;



/**
 * Responsible for querying the server for updates based on what we are
 * currently tracking. It will then add records with all the applicable
 * information returned from the server into the database. 
 * 
 * @author John Jenkins
 * @version 1.0
 */
public class Updater
{
	private static final String TAG = "CENS.Updater";
	
	private static final String SERVER_URL = 
			"http://updater.nexleaf.org/updater/uapp/get/";
	
	private static final String NOTIFICATION_HEADER = "CENS Update Manager";
	private static final String NOTIFICATION_MESSAGE = "Tap here to review updates.";
	private static final String NOTIFICATION_TICKER = "Updates Available";
	public static final int NOTIFICATION_ID = 1;
	
	/**
	 * The name of the group to which this device should be registered.
	 */
	public static final String PREFERENCE_GROUP_NAME = "groupName";
	
	private Context mContext;
	private Database mDatabase;
	private Proxy proxy;
	private AppInfoCache cache = AppInfoCache.get();

	
	private SettingsModel model = SettingsModel.get();
	{
		setupProxy();
	}
	
	/**
	 * Hostname verifier to be used with RestClient.
	 */
	private HostnameVerifier hostnameVerifier = new HostnameVerifier() {
	    @Override
	    public boolean verify(String hostname, SSLSession session) {
	        HostnameVerifier hv =
	            HttpsURLConnection.getDefaultHostnameVerifier();
	        if (hv.verify("coldtrace.org", session)) {
	        	return true;
	        }
	        if (hv.verify("nexleaf.org", session)) {
	        	return true;
	        }
	        if (hv.verify("updater.nexleaf.org", session)) {
	        	return true;
	        }
	        return false;
	    }
	};
	
	RestClient client = new RestClient("http://updater.nexleaf.org");
	
	{
		// init hostname verifier
		client.setHostnameVerifier(hostnameVerifier);
	}
	
	/**
	 * Sets up this Updater.
	 * 
	 * @param context The application Context in which this package runs.
	 */
	public Updater(Context context)
	{
		Log.i(TAG, "Creating a new Updater object.");
		
		mContext = context;
		mDatabase = new Database(context);
		
		/**
		 * This is specific to android 2.2 and 2.3 ... 
		 * We are trying to prevent the 'power saving mode' on some Huawei
		 * phones from affecting our data upload. The power saving mode 
		 * shuts off background data, which breaks everything.
		 * 
		 * The power saving mode also shuts off 'always on mobile', but
		 * that does not seem to affect things if we retry, which we do. 
		 * I have been unable to find a setting for 'always on mobile'.
		 * 
		 * The app must be installed in /system/app/ for this to work since it is 
		 * setting secure setting. Note that any updates to the app put into 
		 * /data/app will still work as long as the permissions in the manifest
		 * match.
		 * 
		 * This runs every time just to make sure.
		 **/
		try {
			Log.d(TAG, "Trying to force background data on !");
			boolean res = Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.BACKGROUND_DATA, 1);
			Log.d(TAG, "Background data force set was " + Boolean.toString(res));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		/**
		 * Below is code for both Android 2.2 and 2.3... the second chunk
		 * works on the 2.3 Huawei phones. Not sure where the first chunk 
		 * will work.
		 * 
		 * This code tries to set the 'Data Enabled' option. This is typically
		 * not touched by the huawei low power settings, but in case it is ever
		 * by accidentally turned off, we can try to force it on. 
		 * 
		 */
		
		/*
		try {	
			Method dataConnSwitchmethod;
			Class telephonyManagerClass;
			Object ITelephonyStub;
			Class ITelephonyClass;

			boolean isEnabled = false;
			
			TelephonyManager telephonyManager = (TelephonyManager) context
	            .getSystemService(Context.TELEPHONY_SERVICE);

			if(telephonyManager.getDataState() == TelephonyManager.DATA_CONNECTED){
				isEnabled = true;
			} else {
				isEnabled = false;  
			}
			Log.d(TAG, "Data state: " + Boolean.toString(isEnabled));

			telephonyManagerClass = Class.forName(telephonyManager.getClass().getName());
			Method getITelephonyMethod = telephonyManagerClass.getDeclaredMethod("getITelephony");
			getITelephonyMethod.setAccessible(true);
			ITelephonyStub = getITelephonyMethod.invoke(telephonyManager);
			ITelephonyClass = Class.forName(ITelephonyStub.getClass().getName());

			//if (isEnabled) {
			//	dataConnSwitchmethod = ITelephonyClass
			//			.getDeclaredMethod("disableDataConnectivity");
			//} else {
			dataConnSwitchmethod = ITelephonyClass.getDeclaredMethod("enableDataConnectivity");   
			//}
			dataConnSwitchmethod.setAccessible(true);
			dataConnSwitchmethod.invoke(ITelephonyStub);

			if(telephonyManager.getDataState() == TelephonyManager.DATA_CONNECTED){
				isEnabled = true;
			} else {
				isEnabled = false;  
			}
			Log.d(TAG, "Data state: " + Boolean.toString(isEnabled));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		*/
		
		try {
			
			final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			final Class conmanClass = Class.forName(conman.getClass().getName());
			final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
			iConnectivityManagerField.setAccessible(true);
			final Object iConnectivityManager = iConnectivityManagerField.get(conman);
			final Class iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
			final Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
			setMobileDataEnabledMethod.setAccessible(true);
				setMobileDataEnabledMethod.invoke(iConnectivityManager, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
			


		
		Log.initialize(context, Database.LOGGER_APP_NAME);
	}
	
	/**
	 * Checks the server for updates. If any are found, they are added to the
	 * database, true is returned, and a notification is sent. Otherwise, 
	 * false is returned.
	 * 
	 * @return True if any updates were found and successfully added to the
	 * 		   database; false, otherwise.
	 */
	public boolean doUpdate()
	{
		String msg = null;
		Exception err = null;
		boolean dataRetrievalError = true;
		boolean rc = false;
		cache.resetDataRetrievalError();
		
		try
		{
			Log.i(TAG, "Beginning update check.");
			
			String response = doGetRequest();
			
			Log.i(TAG, "Got response: " + response);
			
			if(parseResponse(response))
			{
				dataRetrievalError = false;
				NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
				Notification notification = new Notification(R.drawable.u, NOTIFICATION_TICKER, System.currentTimeMillis());
				notification.defaults |= Notification.DEFAULT_LIGHTS;
				notification.flags |= Notification.FLAG_AUTO_CANCEL;
				
				Intent intent = new Intent(mContext, Installer.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				Log.d(TAG, "model .isAutoInstall(): " + model .isAutoInstall());
				if (model.isAutoInstall()) {
					Log.i(TAG, "Updates were found. Started unassisted installation.");
					//Intent intent = new Intent(mContext, Installer.class);
					//intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				    mContext.startActivity(intent);
				} else {
					Log.i(TAG, "Updates were found. Notifying the user.");
					PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
					notification.setLatestEventInfo(mContext, NOTIFICATION_HEADER, NOTIFICATION_MESSAGE, pendingIntent);
					notificationManager.notify(NOTIFICATION_ID, notification);
					
				}
			}
			rc = true;
		}
		catch(ServiceClientException e)
		{
			msg = e.toString();
			err = e;
		}
		catch(MalformedURLException e)
		{
			msg = "There is a problem with the request URL.";
			err = e;
		}
		catch(IOException e)
		{
			msg = "Error while communicating with the server.";
			err = e;
		}
		catch(InvalidParameterException e)
		{
			msg = "Server response was invalid.";
			err = e;
		}
		catch(JSONException e)
		{
			msg = "Error parsing the JSON in the server response.";
			err = e;
		} finally {
			if (err!=null) {
				Log.e(TAG, msg, err);
				StatusModel.get().addDownloadMessage(err.getMessage());
				if (dataRetrievalError) {
					cache.setDataRetrievalError(err.getMessage());
				} else {
					cache.resetDataRetrievalError();
				}
			} else {
			}
		}
		return rc;
	}
	
	/**
	 * Queries the server for all applicable package information and returns
	 * the response without checking it at all.
	 * 
	 * @return A String representing the response from the server.
	 * 
	 * @throws MalformedURLException Thrown if the URL being used to request
	 * 								 information from the server is invalid.
	 * 
	 * @throws IOException Thrown if there is an error while sending or
	 * 					   receiving data from the server.
	 */
	private String doGetRequest() throws MalformedURLException, IOException
	{
		// Begin building the request URL.
		StringBuilder urlBuilder = new StringBuilder(SERVER_URL);

		// Get the device's identifier and add it to the URL.
		String identifier = Utils.getDeviceId();
		urlBuilder.append(identifier);
		
		// Begin building the parameter map.
		Map<String, String> parameters = new HashMap<String, String>();
		
		// Get the package manager.
		PackageManager packageManager = mContext.getPackageManager();
		
		// Get a connection to the database and retrieve the list of managed
		// packages.
		Database db = new Database(mContext);
		LinkedList<String> managed = db.getManaged();
		ListIterator<String> managedIter = managed.listIterator();
		
		// Build a JSON object where each key represents a package and its 
		// value is its version.
		JSONObject results = new JSONObject();
		while(managedIter.hasNext())
		{
			String currPackage = managedIter.next();
			try
			{
				try
				{
					PackageInfo packageInfo = 
						packageManager.getPackageInfo(currPackage, 0);
					results.put(currPackage, packageInfo.versionCode);
				}
				catch(NameNotFoundException e)
				{
					results.put(currPackage, -1);
				}
			}
			catch(JSONException e)
			{
				Log.e(
					TAG,
					"Could not add new record to JSON query: " + currPackage);
			}
		}
		parameters.put("packages", results.toString());
		
		// Get the group and add it to the parameters.
		SharedPreferences preferences = 
			mContext
				.getSharedPreferences(
					Database.PACKAGE_PREFERENCES, 
					Context.MODE_PRIVATE);
		String groupName = 
			preferences
				.getString(
					PREFERENCE_GROUP_NAME, 
					mContext.getString(R.string.default_group));
		parameters.put("group", groupName);
		
		// Build the rest of the URL with the parameters.
		boolean firstPass = true;
		for(String key : parameters.keySet()) {
			if(firstPass) {
				urlBuilder.append('?');
				firstPass = false;
			}
			else {
				urlBuilder.append('&');
			}
			
			urlBuilder.append(URLEncoder.encode(key, "UTF-8"));
			urlBuilder.append('=');
			urlBuilder.append(URLEncoder.encode(parameters.get(key), "UTF-8"));
		}
		
		Log.i(
			TAG, 
			"Sending request for updates with the following GET request: " + 
				urlBuilder.toString());
		
		// Build the URL object and connect to the server.
		String res = client.getAsString(urlBuilder.toString());
		return res;
	}
	
	/**
	 * Parses the response sent back from the server which must have a very
	 * specific layout. If this layout is not held, the entire update will be
	 * aborted to prevent security issues.
	 * 
	 * @param response The response from the server which must follow a very
	 * 				   strict format.
	 * 
	 * @return Returns true if any of the packages in the response were added
	 * 		   to the database meaning that an update needs to be done.
	 * 
	 * @throws InvalidParameterException Thrown if there is any problem with
	 * 									 the server response that isn't
	 * 									 directly connected to JSON parsing.
	 * 
	 * @throws JSONException Thrown if there is any problem with parsing the
	 * 						 JSON in the response.
	 */
	private boolean parseResponse(String response) throws InvalidParameterException, JSONException
	{
		//if (true) throw new InvalidParameterException("Error occured.");
		
		if(response == null)
		{
			throw new InvalidParameterException("HTTP response was null.");
		}
		if(response.length() == 0)
		{
			throw new InvalidParameterException("HTTP response was empty.");
		}
		
		String[] info = response.split(",", 2);
		if((info == null) || (info.length != 2))
		{
			throw new InvalidParameterException("The server replied with an invalid response: " + response);
		}
		
		if((info[0] == null) || (info[1] == null))
		{
			throw new InvalidParameterException("The server replied with an invalid response: " + response);
		}
		
		SharedPreferences preferences = mContext.getSharedPreferences(Database.PACKAGE_PREFERENCES, Context.MODE_PRIVATE);
		
		if(info[0].substring(0, 8).equals("managed=") && (info[0].length() == 9))
		{
			try
			{
				int managedVal = Integer.decode(info[0].substring(8));
				if(managedVal == 0)
				{
					if(preferences.getBoolean(Database.PREFERENCES_MANAGED, false)) {
						// If the user is switching managed state, purge the
						// database of any old updates.
						mDatabase.removeAllUpdates();
					}
					preferences.edit().putBoolean(Database.PREFERENCES_MANAGED, false).commit();
				}
				else if(managedVal == 1)
				{
					if(preferences.getBoolean(Database.PREFERENCES_MANAGED, false)) {
						// If the user is switching managed state, purge the
						// database of any old updates.
						mDatabase.removeAllUpdates();
					}
					preferences.edit().putBoolean(Database.PREFERENCES_MANAGED, true).commit();
				}
				else if(managedVal > 1)
				{
					throw new InvalidParameterException("The value for 'managed' is numeric but invalid: " + managedVal);
				}
			}
			catch(NumberFormatException e)
			{
				throw new InvalidParameterException("The value for 'managed' was non-numeric.");
			}
		}
		else
		{
			throw new InvalidParameterException("The server replied with an invalid response: " + response);
		}
		
		boolean result = false;
		JSONObject jsonPackageInfo;
		JSONArray packages = new JSONArray(info[1]);
		String[] sPackages = new String[packages.length()];
		AppInfoModel packageInfo;
		
		for(int i = 0; i < packages.length(); i++)
		{
			try
			{
				jsonPackageInfo = packages.getJSONObject(i);
				
				PackageInformation.Action action = null;
				String sAction = jsonPackageInfo.getString("action");
				if(sAction.toLowerCase().equals("clean"))
				{
					action = Action.CLEAN;
				}
				else if(sAction.toLowerCase().equals("update"))
				{
					action = Action.UPDATE;
				}
				else
				{
					Log.e(TAG, "Invalid 'Action' value. Defaulting to update.");
					action = Action.UPDATE;
				}
				
				packageInfo = new AppInfoModel(jsonPackageInfo.getString("package"), 
													 jsonPackageInfo.getString("release"),
													 jsonPackageInfo.getString("name"),
													 jsonPackageInfo.getInt("ver"), 
													 jsonPackageInfo.getString("url"),
													 action);
				result |= updatePackage(packageInfo);
				
				sPackages[i] = packageInfo.getQualifiedName();
				cache.add(packageInfo);
			}
			catch(JSONException e)
			{
				Log.e(TAG, "Malformed JSON data; skipping package: " + packages.getString(i), e);
				continue;
			}
			catch(IllegalArgumentException e)
			{
				Log.e(TAG, "Invalid package information.", e);
				continue;
			}
		}
		
		checkForMissingManagedPackages(sPackages);
		
		return result;
	}
	
	/**
	 * Checks the list of packages we were just given and compares it against
	 * the list of packages we are managing. If there is something we are
	 * managing but no package information is being sent any longer, we will
	 * assume that the package is no longer being hosted by the server and 
	 * will mark it as needing to be uninstalled.
	 * 
	 * Note: In a more robust version with multiple download sources, this may
	 * 		 not be necessary as the package may have simply migrated to a new
	 * 		 host.
	 * 
	 * @param givenPackages A list of packages that were given by the server.
	 */
	private void checkForMissingManagedPackages(String[] givenPackages)
	{
		LinkedList<String> managedPackages = mDatabase.getManaged();
		ListIterator<String> iter = managedPackages.listIterator();
		
		while(iter.hasNext())
		{
			String currPackage = iter.next();
			boolean packageFound = false;
			for(int j = 0; j < givenPackages.length; j++)
			{
				if(givenPackages[j].equals(currPackage))
				{
					packageFound = true;
					break;
				}
			}

			if(!packageFound)
			{
				Log.i(TAG, "Currently managed package, " + currPackage + ", was not found in the update list, so it will no longer be managed. Any pending updates for this package are also being removed.");
				mDatabase.stopManaging(currPackage);
				mDatabase.removeUpdate(currPackage);
			}
		}
	}
	
	/**
	 * Takes a PackageInformation object that has been sanitized and checks
	 * its values against what is currently installed to determine if an
	 * update needs to take place.
	 * 
	 * @param packageInformation The information pertaining to a single
	 * 							 package that needs to be checked against
	 * 							 what is current installed and what is 
	 * 							 currently being monitored.
	 * 
	 * @return Returns true if the package needs to be updated and the update
	 * 		   information was successfully added to the database.
	 */
	private boolean updatePackage(AppInfoModel packageInformation)
	{
		boolean result = false;
		PackageManager packageManager = mContext.getPackageManager();
		SharedPreferences preferences = mContext.getSharedPreferences(Database.PACKAGE_PREFERENCES, Context.MODE_PRIVATE);
		
		try
		{
			// Check to see if the package is currently installed. If not, we
			// will effectively jump to the "NameNotFoundException".
			PackageInfo packageInfo = packageManager.getPackageInfo(packageInformation.getQualifiedName(), 0);
			//packageInfo;
			
			// The package is installed, and we are managing it.
			if(mDatabase.isManaged(packageInformation.getQualifiedName()))
			{
				result = checkInstalledVersionVsUpdate(packageInfo, packageInformation);
			}
			// The package is installed, but we are not managing it.
			else
			{
				// We should begin managing it if the user is a managed user.
				if(preferences.getBoolean(Database.PREFERENCES_MANAGED, false))
				{
					Log.i(TAG, "We received an update for package, " + packageInformation.getQualifiedName() + ", which we weren't managing but is installed and this is a managed user. Therefore, we are adding it to the list of managed packages.");
					mDatabase.addManaged(packageInformation.getQualifiedName(), packageInformation.getDisplayName());
					
					result = checkInstalledVersionVsUpdate(packageInfo, packageInformation);
				}
				// Otherwise, ignore it and make sure we don't store any
				// updates about it.
				else
				{
					Log.i(TAG, "We received an update for package, " + packageInformation.getQualifiedName() + ", which we aren't managing and this user isn't managed, so we are going to ignore it and remove any updates in the database pertaining to it.");
					mDatabase.removeUpdate(packageInformation.getQualifiedName());
				}
			}
		}
		// The package is not currently installed.
		catch(NameNotFoundException e)
		{
			// If the user is a managed user, but they don't have the package
			// installed for some reason, we will forcibly install it.
			if(preferences.getBoolean(Database.PREFERENCES_MANAGED, false))
			{
				Log.i(TAG, "This user is managed and the package, " + packageInformation.getQualifiedName() + ", is not installed. Therefore, we will begin managing it and add it to the list of updates.");
				mDatabase.addManaged(packageInformation.getQualifiedName(), packageInformation.getDisplayName());
				result = true;
			}
			else
			{
				Log.i(TAG, "This user is not managed and the package, " + packageInformation.getQualifiedName() + ", is not installed. Therefore, we will not begin managing it but will add it to the list of updates to be managed if the user so desires..");
			}
			
			// I do the "&" so that if the value was still false, i.e. they
			// aren't managed, then I don't want to indicate that a
			// notification should be sent.
			result &= addAsUpdate(packageInformation);
		}
		
		return result;
	}
	
	/**
	 * Checks the version of an installed package versus the version we just
	 * received in an update and compares the two. 
	 * 
	 * If the update has a greater version number, we will add this to the
	 * list of updates.
	 * 
	 * If the update has a lesser version number, we will remove all current
	 * updates of that package. This may be the case that we are trying to 
	 * roll back an update in which case we should force this update. It also
	 * may be the case that we are a developer with a newer version of the
	 * code we are testing out for ourselves, and we wouldn't want an update
	 * all-of-a-sudden rewriting what we have done. So, we would want to
	 * ignore all updates. For now, we will err on the side of the developer.
	 * 
	 * If the update has the same version as the installed version we assume
	 * everything is going swimmingly and ignore it.
	 * 
	 * @param db An accessor to the database.
	 * 
	 * @param packageInfo A PackageInfo object returned by the system about
	 * 					  the currently installed package.
	 * 
	 * @param packageInformation A PackageInformation object that was created
	 * 							 from the information given by this update.
	 * 
	 * @return Returns true iff we added an update to the database and the
	 * 		   database took it, false otherwise.
	 */
	private boolean checkInstalledVersionVsUpdate(PackageInfo packageInfo, AppInfoModel packageInformation)
	{
		boolean result = false;
		String msg;
		packageInformation.installedVersion = packageInfo.versionCode;
		packageInformation.lastChecked = new Date();
		String checkedMessage = "";
		if(packageInfo.versionCode < packageInformation.getVersion())
		{
			msg = "We received an update of the package, " + packageInformation.getQualifiedName() + ", so we will add it to the list of updates.";
			checkedMessage = "Needs update";
			// We are not up-to-date.
			Log.i(TAG, msg);
			result = addAsUpdate(packageInformation);
		}
		else if(packageInfo.versionCode > packageInformation.getVersion())
		{
			// We have a future version!?
			// For now, we are ignoring this case and assuming that
			// the old version number is an error and not going to
			// corrupt ourselves (further).
			checkedMessage = "Ahead";			
			msg = "We received an update of the package, " + packageInformation.getQualifiedName() + ", where the installed version is greater than this 'update'. Therefore, we will remove any pending updates for this package.";
			Log.i(TAG, msg);
			mDatabase.removeUpdate(packageInformation.getQualifiedName());
		}
		else
		{
			checkedMessage = "Up-to-date";			
			// Everything is in sync.
			msg = "We received an update of the package, " + packageInformation.getQualifiedName() + ", with the same version as the one we have now, so we will remove any pending, unnecessary updates.";
			Log.i(TAG, msg);
			mDatabase.removeUpdate(packageInformation.getQualifiedName());
		}
		StatusModel.get().addDownloadMessage(msg);
		packageInformation.lastCheckedMessage = checkedMessage;
		//packageInformation.lastInstallTime = packageInfo.;
		return result;
	}
	
	/**
	 * Attempts to add the package information to the database. Returns
	 * whether or not the database insertion succeeds.
	 * 
	 * @param packageInfo The information to be added to the database for the
	 * 					  upgrade.
	 * 
	 * @return Returns true if the package was successfully added to the 
	 * 		   database and false if any error occurs.
	 */
	private boolean addAsUpdate(PackageInformation packageInfo)
	{
		try
		{
			mDatabase.addUpdate(packageInfo);
			return true;
		}
		catch(SQLException e)
		{
			Log.e(TAG, "Error while adding new package to the database.", e);
			return false;
		}
	}
	
    /**
     * Sets up common request features, such as User-Agent, proxy etc.
     * @param request
     */
    private void setupProxy() {
    	if (Constants.USE_PROXY) {
//    		Proxy proxy = new Proxy(Proxy.HTTP, new InetSocketAddress("10.0.0.1", 8080));
    		proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(Constants.PROXY_HOST, Constants.PROXY_PORT));
    		//System.setProperty("http.proxyPort", Integer.toString(Constants.PROXY_PORT));
    		//System.setProperty("http.proxyHost", Constants.PROXY_HOST);
    	}
	}
	
}
