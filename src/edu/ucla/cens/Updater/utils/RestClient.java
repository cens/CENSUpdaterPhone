package edu.ucla.cens.Updater.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.json.JSONObject;

import android.util.Log;

/**
 * Generic RESTful client that supports GET and POST using JSON or String 
 * request body and response.
 *
 */
public class RestClient {
	
	private static final String TAG = RestClient.class.getSimpleName();

	/**
	 * Proxy to be optionally used or all HTTP communication.
	 */
    private HttpHost proxy = new HttpHost(Constants.PROXY_HOST, Constants.PROXY_PORT);
	
	/**
	 * User-Agent http header sent with requests.
	 */
	private static final String USER_AGENT = "updater/1.1";
	
	/**
	 * Base URL such as http://localhost/TempTrace/configSync/
	 */
	private String baseUrl;

	private HostnameVerifier hostnameVerifier  = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
	
	// default client template for creating connections
	private DefaultHttpClient client = new DefaultHttpClient();
	
	// Connection manager used for verified connections
	private SingleClientConnManager mgr;
	
	{
		// init hostname verifier for client and mgr
		SchemeRegistry registry = new SchemeRegistry();
		SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getSocketFactory();
		socketFactory.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
		registry.register(new Scheme("https", socketFactory, 443));
		mgr = new SingleClientConnManager(client.getParams(), registry);
	}

	static {
		HttpsURLConnection.setDefaultHostnameVerifier( new HostnameVerifier(){
			public boolean verify(String string,SSLSession ssls) {
				return true;
			}
		});		
		
	}
    
	public String getBaseUrl() {
		return baseUrl;
	}

	public RestClient(String url) {
		this.baseUrl = url;
	}

	/**
	 * Converts receiving stream to string.
	 * @param is input stream
	 * @return string created from stream
	 */
    public static String convertStreamToString(InputStream is) {
        /*
         * To convert the InputStream to String we use the
         * BufferedReader.readLine() method. We iterate until the BufferedReader
         * return null which means there's no more data to read. Each line will
         * appended to a StringBuilder and returned as String.
         * 
         * (c) public domain:
         * http://senior.ceng.metu.edu.tr/2009/praeda/2009/01/
         * 11/a-simple-restful-client-at-android/
         */
        BufferedReader reader = new BufferedReader(new InputStreamReader(is), 8192);
        StringBuilder sb = new StringBuilder();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
    	
    /**
     * Performs GET request.
     * @param baseUrl
     * @return a JSONObject received from server
     * @throws ServiceClientException
     */
    public JSONObject get(String url) throws ServiceClientException {
		HttpGet request = new HttpGet(url);
        setupRequest(request);
		return processRequestAsJson(request);
    }

    /**
     * Performs GET request, returning response as String.
     * @param baseUrl
     * @return a String response received from server
     * @throws ServiceClientException
     */
    public String getAsString(String url) throws ServiceClientException {
		HttpGet request = new HttpGet(url);
        setupRequest(request);
		return processRequestAsString(request);
    }
    
    /**
     * Performs GET request, returning response as JSONObject.
     * @param baseUrl
     * @return a JSON response received from server
     * @throws ServiceClientException
     */
    public JSONObject get() throws ServiceClientException {
    	return get(this.baseUrl);
    }
    
    /**
     * Performs post request.
     * @param url URL to post to
     * @param body JSONObject body to post to server
     * @return a JSONObject received from server
     * @throws ServiceClientException
     */
    public JSONObject post(String url, JSONObject body) 
      throws ServiceClientException {
        // Prepare a request object
        String bodystr = body.toString();
        String result = post(url, bodystr);
        try {
	        JSONObject json = new JSONObject(result);
	        Log.i(TAG, "received json: " + json.toString(2));
	        return json;
        } catch(Exception ex) {
        	throw new ServiceClientException(ex);
        }
    }
    
    /**
     * Performs post request.
     * Uses URL specified ctor at init time.
     * @param body JSONObject body to post to server
     * @return a JSONObject received from server
     * @throws ServiceClientException
     */
    public JSONObject post(JSONObject body) throws ServiceClientException {
    	return post(this.baseUrl, body);
    }
    
    /**
     * Performs post request.
     * @param url URL to post to
     * @param body string body to post to server
     * @return a String received from server
     * @throws ServiceClientException
     */
    public String post(String url, String body) 
      throws ServiceClientException {
        // Prepare a request object
        HttpPost request = new HttpPost(url);
        setupRequest(request);
        Log.d(TAG,"post body: " + body);
        try {
        	StringEntity se = new StringEntity(body);
        	//se.setContentType("application/json");
        	se.setContentType("application/x-www-form-urlencoded");
        	
			request.setEntity(se);
			return processRequestAsString(request);
		} catch (UnsupportedEncodingException e) {
			throw new ServiceClientException(e);
		}
    }


    /**
     * Sets up common request features, such as User-Agent, proxy etc.
     * @param request
     */
    private void setupRequest(HttpRequestBase request) {
        request.setHeader("User-Agent", USER_AGENT);
    	if (Constants.USE_PROXY) {
            request.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
    	}
	}

	/**
     * Performs post request.
     * Uses URL specified ctor at init time.
     * @param body string body to post to server
     * @return a String received from server
     * @throws ServiceClientException
     */
    public String post(String body) throws ServiceClientException {
    	return post(this.baseUrl, body);
    }
    
    /**
     * Performs processing of request
     * @param request HTTP post or get request object
     * @return a JSONObject received from server
     * @throws ServiceClientException if processing resulted in error
     */
	private JSONObject processRequestAsJson(HttpRequestBase request) 
	  throws ServiceClientException {
        String result = processRequestAsString(request);
        try {
	        JSONObject json = new JSONObject(result);
	        Log.i(TAG, "received json: " + json.toString(2));
	        return json;
        } catch(Exception ex) {
        	throw new ServiceClientException(ex);
        }
    }
    
    /**
     * Performs processing of request
     * @param request HTTP post or get request object
     * @return a String received from server
     * @throws ServiceClientException if processing resulted in error
     */
	private String processRequestAsString(HttpRequestBase request) 
	  throws ServiceClientException {
		
		//DefaultHttpClient httpclient = new DefaultHttpClient(mgr, client.getParams());
		DefaultHttpClient httpclient = new DefaultHttpClient(client.getParams());
        HttpResponse response = null;
        String result = null;
        try {
        	response = httpclient.execute(request);
        } catch(Exception e) {
        	e.printStackTrace();
        	throw new ServiceClientException("Problem executing GET request", e);
        }
        StatusLine status = response.getStatusLine();
        if (status.getStatusCode() >= 300) {
        	String msg = "GET received error response: " + status;
            Log.e(TAG, msg);
            throw new ServiceClientException(msg);
        } else {
            Log.i(TAG, "GET received response: " + status);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try {
	                // JSON Response Read
	                InputStream instream = entity.getContent();
	                result = convertStreamToString(instream);
                    Log.d(TAG, "result: " + result);
                } catch(Exception ex) {
                	ex.printStackTrace();
                	throw new ServiceClientException(ex);
                }
            }            	
        }
        return result;
    }

	public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
		this.hostnameVerifier = hostnameVerifier;
	}
	
}
