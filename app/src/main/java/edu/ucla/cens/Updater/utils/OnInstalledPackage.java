package edu.ucla.cens.Updater.utils;

/**
 * Package installed notifier
 *
 */
public interface OnInstalledPackage {
	
	public void packageInstalled(String packageName, int returnCode, String message);
	public void packageUninstalled(String packageName, int returnCode, String message);

}
