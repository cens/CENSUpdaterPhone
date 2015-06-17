package edu.ucla.cens.Updater;

/**
 * Based on what the database stores for managed applications, this wrapper
 * class allows for encapsulation of an application's information to easily be
 * passed around and managed. This doesn't have any actual connections to the
 * database and is only used as a basic wrapper class.
 * 
 * @author John Jenkins
 * @version 1.0
 */
public class PackageDescription
{
	private String qualifiedName;
	private String displayName;
	
	/**
	 * The only constructor available because these objects should be thought
	 * of as a single one-time use object. They should be instantiated with 
	 * values and those values should never be changed, only retrieved.
	 * 
	 * @param qualifiedName The qualified name of this package.
	 * 
	 * @param displayName A user-friendly name for this package.
	 * 
	 * @throws IllegalArgumentException Thrown if any of the values are null
	 * 									or invalid for their respective	
	 * 									attribute.
	 */
	PackageDescription(String qualifiedName, String displayName) throws IllegalArgumentException
	{
		// TODO: Create a more extensive cleaning process for this information.
		if(qualifiedName == null)
		{
			throw new IllegalArgumentException("'qualifiedName' cannot be null.");
		}
		else if(displayName == null)
		{
			throw new IllegalArgumentException("'displayName' cannot be null.");
		}
		
		this.qualifiedName = qualifiedName;
		this.displayName = displayName;
	}
	
	/**
	 * The qualified name of this package of the form com.example.package
	 * 
	 * @return The qualified name of this package.
	 */
	public String getQualifiedName()
	{
		return qualifiedName;
	}
	
	/**
	 * A name for this package that can be displayed to the user such as
	 * "Application Manager".
	 * 
	 * @return A user-friendly name for this package.
	 */
	public String getDisplayName()
	{
		return displayName;
	}
	
	/**
	 * Returns the user-friendly name of this package as this is mostly used
	 * by internal functions so this class can be used by lists and arrays.
	 */
	@Override
	public String toString()
	{
		return displayName;
	}
}
