package edu.ucla.cens.Updater;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Very basic wrapper class for package information. This way, we can create a
 * single PackageInfo object that will contain all the information related to
 * a package without bothering with the specifics.
 * 
 * The values can only be set at instantiation time because there should never
 * be a need to change only part of the values. This could lead to unnecessary
 * object inconsistencies.
 * 
 * Note: This class is responsible for the sanitation of package information.
 * 
 * @author John Jenkins
 * @version 1.0
 */
public class PackageInformation extends PackageDescription
{
	private String releaseName;
	private int version;
	private String url;
	private Action action;
	
	private boolean toBeApplied;

	public enum Action { CLEAN, UPDATE };
	
	/**
	 * Builds an object and is the only way to set the values of this object.
	 * 
	 * @param qualifiedName The qualified name of the package such as
	 * 						"edu.ucla.cens.Updater" that is used by the system
	 * 						to differentiate different packages.
	 * 
	 * @param displayName The name that should be displayed to the user when
	 * 					  this package is shown such as "Updater".
	 * 
	 * @param version The version of this application as managed by the
	 * 				  developers and updater server.
	 * 
	 * @param url The URL where this package can be downloaded from.
	 * 
	 * @param action The action to be taken when installing this app over an
	 * 				 existing version of the same app.
	 * 
	 * @throws IllegalArgumentException Thrown if any of the values are null
	 * 									or invalid for their respective	
	 * 									attribute.
	 */
	public PackageInformation(String qualifiedName, String releaseName, String displayName, int version, String url, Action action) throws IllegalArgumentException
	{
		super(qualifiedName, displayName);

		// TODO: Create a more extensive cleaning process for this information.
		if(releaseName == null)
		{
			throw new IllegalArgumentException("Release name is null.");
		}
		else if(version < 0)
		{
			throw new IllegalArgumentException("Invalid version: " + version);
		}
		else if(action == null)
		{
			throw new IllegalArgumentException("Action is null.");
		}
		
		try
		{
			new URL(url);
		}
		catch(MalformedURLException e)
		{
			throw new IllegalArgumentException("Invalid URL: " + url);
		}
		
		this.releaseName = releaseName;
		this.version = version;
		this.url = url;
		this.action = action;
		
		toBeApplied = false;
	}
	
	/**
	 * Returns the String associated with this release.
	 * 
	 * @return A String associated with this release such as "final", "beta",
	 * 		   etc.
	 */
	public String getReleaseName()
	{
		return releaseName;
	}
	
	/**
	 * The version of this package as reported by the uploader. This must be
	 * the same as what the package will report to Android after installation.
	 * 
	 * @return The value set for the version of this package.
	 */
	public int getVersion()
	{
		return version;
	}
	
	/**
	 * A URL where this package can be or was downloaded from. There is no
	 * guarantee that this URL is still valid.
	 * 
	 * @return The value set for the URL where this package was downloaded or
	 * 		   may be downloaded from.
	 */
	public String getUrl()
	{
		return url;
	}
	
	/**
	 * Returns the action that should be taken when installing this update.
	 * 
	 * @return An enum value describing how this update should be applied.
	 */
	public Action getAction()
	{
		return action;
	}
	
	/**
	 * Sets whether or not this update should be applied.
	 * 
	 * @param value Whether or not this update should be applied.
	 */
	public void setToBeApplied(boolean value)
	{
		toBeApplied = value;
	}
	
	/**
	 * Returns whether or not this update should be applied. If this value has
	 * not been set it will default to false.
	 * 
	 * @return Whether or not this update should be applied.
	 */
	public boolean getToBeApplied()
	{
		return toBeApplied;
	}
}