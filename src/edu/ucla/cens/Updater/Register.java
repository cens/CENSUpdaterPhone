package edu.ucla.cens.Updater;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
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
			"http://systemsens.cens.ucla.edu/updates/updater/register/";

	private static final String JSON_KEY_SIM_ID = "sim_id";
	private static final String JSON_KEY_PHONE_NUMBER = "phone_number";
	private static final String JSON_KEY_ASSET_TAG = "asset_tag";
	private static final String JSON_KEY_GROUP_NAME = "group_name";

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
	public Register(final Context context, final String assetTag,
			final String groupName) {

		if (context == null) {
			throw new NullPointerException("The Context is null.");
		}
		if (assetTag == null) {
			throw new NullPointerException("The asset tag is null.");
		}
		if (assetTag.length() == 0) {
			throw new IllegalArgumentException("The asset tag is invalid.");
		}
		if (groupName == null) {
			throw new NullPointerException("The group name is null.");
		}
		if (groupName.length() == 0) {
			throw new IllegalArgumentException("The group name is invalid.");
		}

		Log.i(TAG, "Creating a Register object.");

		mContext = context;
		mAssetTag = assetTag;
		mGroupName = groupName;
	}

	/**
	 * Registers this device with the server.
	 */
	public void doRegister() {
		try {
			Log.i(TAG, "Beginning the registration.");

			String response = doPostRequest();

			Log.i(TAG, "Got response: " + response);

			if (parseResponse(response)) {
				Log.i(TAG, "Registration was successful. Notifying the user.");

				Toast.makeText(mContext, "Registration successful.",
						Toast.LENGTH_LONG).show();
			}
		} 
		catch (MalformedURLException e) {
			Log.e(TAG, "There is a problem with the request URL.", e);
			
			Toast.makeText(
					mContext, 
					"Registration failed.", 
					Toast.LENGTH_LONG)
				.show();
		} 
		catch (IOException e) {
			Log.e(TAG, "Error while communicating with the server.", e);
			
			Toast.makeText(
					mContext, 
					"Registration failed.", 
					Toast.LENGTH_LONG)
				.show();
		} 
		catch (JSONException e) {
			Log.e(TAG, "Error parsing the JSON in the server response.", e);
			
			Toast.makeText(
					mContext, 
					"Registration failed.", 
					Toast.LENGTH_LONG)
				.show();
		}
	}

	/**
	 * Posts the necessary information to the server and then returns the
	 * response from the server as a string.
	 * 
	 * @return The response from the server as a string.
	 * 
	 * @throws MalformedURLException
	 *             Thrown if the URL is invalid.
	 * 
	 * @throws IOException
	 *             Thrown if there is a problem communicating with the server.
	 * 
	 * @throws JSONException
	 *             Thrown if there is a problem building the data to be sent to
	 *             the server.
	 */
	private String doPostRequest() throws MalformedURLException, IOException,
			JSONException {

		TelephonyManager telephonyManager = (TelephonyManager) mContext
				.getSystemService(Context.TELEPHONY_SERVICE);

		String identifier = telephonyManager.getDeviceId();

		String simId = telephonyManager.getSimSerialNumber();
		String phoneNumber = telephonyManager.getLine1Number();

		JSONObject info = new JSONObject();
		info.put(JSON_KEY_SIM_ID, simId);
		info.put(JSON_KEY_PHONE_NUMBER, phoneNumber);
		info.put(JSON_KEY_ASSET_TAG, mAssetTag);
		info.put(JSON_KEY_GROUP_NAME, mGroupName);

		HttpPost httpPost = new HttpPost(SERVER_URL + identifier);
		HttpParams httpParams = new BasicHttpParams();
		httpParams.setParameter(HTTP_KEY_DATA, info.toString());
		httpPost.setParams(httpParams);

		HttpClient httpClient = new DefaultHttpClient();
		HttpResponse response = httpClient.execute(httpPost);

		int amountRead;
		byte[] chunk = new byte[4096];
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		InputStream is = response.getEntity().getContent();
		while ((amountRead = is.read(chunk)) != -1) {
			baos.write(chunk, 0, amountRead);
		}
		return baos.toString();
	}

	/**
	 * Parses the response from the server and returns true if the server
	 * successfully registered this phone. Otherwise, it returns false.<br />
	 * <br />
	 * This is what needs to be updated if/when we decide to do authentication
	 * or have a more robust communication with the server.
	 * 
	 * @param response
	 *            The response from the server as a string.
	 * 
	 * @return True if the server successfully registered the device; false,
	 *         otherwise.
	 * 
	 * @throws JSONException
	 *             Thrown if there is an error parsing the response from the
	 *             server.
	 */
	private boolean parseResponse(final String response) throws JSONException {

		JSONObject jsonResponse = new JSONObject(response);
		if ("success".equals(jsonResponse.get("result"))) {
			return true;
		}

		return false;
	}
}