package edu.ucla.cens.Updater.utils;

public class Constants {

	//static final String SERVER_URL = "http://apps.ohmage.org/uproject/uapp/get/";
	static final String SERVER_URL = "https://updater.nexleaf.org/updater/uapp/get/";
	/**
	 Set to true to use a proxy - useful if you need
	  to trace HTTP traffic using a proxy.
	  Always set to false for PRODUCTION.
	 */
	public static final boolean USE_PROXY = false;
	
	/**
	 * If USE_PROXY is enabled, used to proxy host.
	 */
	//public static final String PROXY_HOST = "192.168.2.110";
	public static final String PROXY_HOST = "192.168.1.83";
	/**
	 * If USE_PROXY is enabled, used to proxy port.
	 */
	public static final int PROXY_PORT = 8008;
	
	
}
