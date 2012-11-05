package edu.ucla.cens.Updater;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Registers this device with the server by providing information specific to
 * the device at the time of registration.
 * 
 * @author John Jenkins
 */
public class Register {
	private static final String TAG = "CENS.Register";

	private static final String SERVER_URL = 
			"http://apps.ohmage.org/uproject/uapp/register/";

	private static final String JSON_KEY_PHONE_ID = "id";
	private static final String JSON_KEY_SIM_ID = "sim_id";
	private static final String JSON_KEY_PHONE_NUMBER = "phone_number";
	private static final String JSON_KEY_ASSET_TAG = "asset_tag";
	private static final String JSON_KEY_GROUP_NAME = "group_name";
	
	private static final String JSON_KEY_RESULT = "result";
	private static final String JSON_VALUE_SUCCESS = "success";

	private static final String HTTP_KEY_DATA = "info";

	private final Context mContext;
	private final String mAssetTag;
	private final String mGroupName;

	/**
	 * Creates a new registration object.
	 * 
	 * @param context
	 *            The Context in which this registration is running.
	 * 
	 * @param assetTag
	 *            The phone's asset tag value.
	 * 
	 * @param groupName
	 *            The group to which this phone should now belong.
	 * 
	 * @throws NullPointerException
	 *             If any of its parameters are null.
	 * 
	 * @throws IllegalArgumentException
	 *             If any of its parameters are obviously invalid.
	 */
	public Register(
			final Context context, 
			final String assetTag,
			final String groupName) {

		Log.i(TAG, "Creating a Register object.");

		if (context == null) {
			throw new NullPointerException("The Context is null.");
		}
		if (groupName == null) {
			throw new NullPointerException("The group name is null.");
		}
		if (groupName.length() == 0) {
			throw new IllegalArgumentException("The group name is invalid.");
		}

		mContext = context;
		mAssetTag = assetTag;
		mGroupName = groupName;
	}

	/**
	 * Registers this device with the server.
	 * 
	 * @return Returns true iff the registration succeeds.
	 */
	public boolean doRegister() {
		boolean failed = true;
		
		try {
			Log.i(TAG, "Beginning the registration.");

			doPostRequest();
			
			failed = false;
			Log.i(TAG, "Registration was successful.");
		}
		catch (IOException e) {
			Log.e(TAG, "Error while communicating with the server.", e);
		}
		catch (IllegalStateException e) {
			Log.e(TAG, "An internal error occurred.", e);
		}
		finally {
			Toast.makeText(
					mContext, 
					(failed) ? "Registration failed." : "Registration succeeded.", 
					Toast.LENGTH_LONG)
				.show();
		}
		
		return failed;
	}

	/**
	 * Posts the necessary information to the server and then checks the HTTP
	 * status code and response to see if the server succeeded.
	 * 
	 * @throws IOException
	 *             Thrown if there is a problem communicating with the server.
	 *             
	 * @throws IllegalStateException
	 * 			   Thrown if the there is an internal problem making the
	 * 			   request.
	 */
	private void doPostRequest() throws IOException {

		TelephonyManager telephonyManager = (TelephonyManager) mContext
				.getSystemService(Context.TELEPHONY_SERVICE);

		String identifier = telephonyManager.getDeviceId();

		String simId = telephonyManager.getSimSerialNumber();
		String phoneNumber = telephonyManager.getLine1Number();

		JSONObject info = new JSONObject();
		try {
			info.put(JSON_KEY_PHONE_ID, identifier);
			info.put(JSON_KEY_SIM_ID, simId);
			info.put(JSON_KEY_PHONE_NUMBER, phoneNumber);
			info.put(JSON_KEY_ASSET_TAG, mAssetTag);
			info.put(JSON_KEY_GROUP_NAME, mGroupName);
		}
		catch(JSONException e) {
			throw new IllegalStateException(
					"There was an error creating the JSON data.",
					e);
		}
		
		URL url;
		try {
			url = new URL(SERVER_URL);
		}
		catch(MalformedURLException e) {
			throw new IllegalStateException(
					"The server URL is invalid: " +
						SERVER_URL);
		}
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		
		connection.connect();
		
		OutputStream out = connection.getOutputStream();
		out.write(
				(HTTP_KEY_DATA + "=" + URLEncoder.encode(info.toString()))
				.getBytes("UTF8")
			);
		out.flush();
	
		int responseCode = connection.getResponseCode();
		if(responseCode == HttpURLConnection.HTTP_OK) {
			JSONObject response = 
					getResponseMessage(connection.getInputStream());
			
			try {
				String resultString = response.getString(JSON_KEY_RESULT);
				if(! resultString.equals(JSON_VALUE_SUCCESS)) {
					throw new IllegalStateException(
							"There was an error with the upload: " +
								resultString);
				}
			}
			catch(JSONException e) {
				throw new IllegalStateException(
						"The server returned a success HTTP response code, " +
							"but the actual response is invalid: " +
							response.toString(),
						e);
			}
		}
		else if(responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
			JSONObject response =
					getResponseMessage(connection.getErrorStream());
			
			try {
				String resultString = response.getString(JSON_KEY_RESULT);
				throw new IllegalStateException(
						"Got HTTP error (" + 
							connection.getResponseCode() +
							"): " +
							resultString);
			}
			catch(JSONException e) {
				throw new IllegalStateException(
						"The server returned a success HTTP response code, " +
							"but the actual response is invalid: " +
							response.toString(),
						e);
			}
		}
		else {
			throw new IllegalStateException(
					"The server returned an unknown error code.");
		}
	}
	
	/**
	 * Returns the JSONObject from the server.
	 * 
	 * @param is The input stream generated by the connection to the server.
	 * 
	 * @return The response from the server as a JSONObject.
	 * 
	 * @throws IllegalStateException Thrown if there was an error reading or
	 * 								 decoding the response from the server.
	 */
	private JSONObject getResponseMessage(InputStream is) {
		try {
			int bytesRead;
			byte[] buffer = new byte[4096];
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			
			while((bytesRead = is.read(buffer)) != -1) {
				baos.write(buffer, 0, bytesRead);
			}
			
			return new JSONObject(baos.toString());
		}
		catch(IOException e) {
			throw new IllegalStateException(
					"Error reading the response from the input stream.", 
					e);
		}
		catch(JSONException e) {
			throw new IllegalStateException(
					"Error decoding the response from the server.",
					e);
		}
	}
}