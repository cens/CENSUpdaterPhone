package edu.ucla.cens.Updater;

import java.security.InvalidParameterException;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import edu.ucla.cens.Updater.PackageInformation.Action;
import edu.ucla.cens.systemlog.Log;

/**
 * Database interface class.
 * 
 * This class is responsible for providing an API for the database to store
 * package information including what packages need to be updated, where to
 * get those updates, when packages were last updated, when packages were
 * deleted, etc. This only applies to packages that the user has specified
 * that they want this application to track.
 * 
 * @author John Jenkins
 * @version 1.0
 */
public class Database 
{
	private static final String TAG = "CENS.Updater.Database";
	
	// Database constants.
	private static final String DB_NAME = "Updater";
	private static final int DB_VERSION = 1;
	
	// Table - Packages to be installed
	private static final String PACKAGES_TO_BE_INSTALLED = "packages_to_be_installed";
	private static final String ID = "_id";
	/**
	 * The descriptor for the qualified name of the package such as 
	 * "edu.ucla.cens.Updater".
	 */
	public static final String PACKAGE = "package";
	/**
	 * The descriptor for the displayed name of the package such as 
	 * "CENS Updater".
	 */
	public static final String RELEASE_NAME = "release";
	/**
	 * The name of the app for displaying to the user.
	 */
	public static final String APP_NAME = "name";
	/**
	 * The descriptor for the version of the application in integer form as
	 * required by the Android system.
	 */
	public static final String APP_VERSION = "version";
	/**
	 * The descriptor for the URL from which the package can be downloaded.
	 */
	public static final String URL = "url";
	/**
	 * The action to be taken when this package is being updated such as 
	 * "clean", "update", etc.
	 */
	public static final String ACTION = "action";
	/**
	 * Indicates whether or not the update should be applied.
	 */
	public static final String TO_BE_APPLIED = "to_be_applied";
	
	// Table - Managed Packages
	private static final String MANAGED_PACKAGES = "managed_packages";
	
	// Table - Packages to be Uninstalled
	private static final String PACKAGES_TO_BE_UNINSTALLED = "packages_to_be_uninstalled";
	
	private static final int ACTION_CLEAN = 1;
	private static final int ACTION_UPDATE = 2;
	
	/**
	 * The name of the preferences file used by Android to lookup our
	 * preferences.
	 */
	public static final String PACKAGE_PREFERENCES = "edu.ucla.cens.Updater.Preferences.Packages";
	/**
	 * The key for whether or not this user is managed.
	 */
	public static final String PREFERENCES_MANAGED = "isManaged";
	/**
	 * The key for whether or not we are attempting a self-update.
	 */
	public static final String PREFERENCES_SELF_UPDATE = "selfUpdate";
	
	/**
	 * The name of the application according to the logger.
	 */
	public static final String LOGGER_APP_NAME = "CENSAppManager";
	
	/**
	 * Basic helper for the database to manage creating, updating, and
	 * retrieving the database.
	 * 
	 * This code was primarily modeled after the reference code supplied by
	 * Google.
	 * 
	 * @author John Jenkins
	 * @version 1.0
	 */
	private static class OpenHelper extends SQLiteOpenHelper
	{
		/**
		 * Calls the parents constructor which sets up the database.
		 * 
		 * @param context This application's Context.
		 */
		OpenHelper(Context context)
		{
			super(context, DB_NAME, null, DB_VERSION);
		}
		
		/**
		 * Runs a SQL query that creates the database.
		 */
		@Override
		public void onCreate(SQLiteDatabase db)
		{
			Log.i(TAG, "Creating database.");
			
			db.execSQL("CREATE TABLE " + PACKAGES_TO_BE_INSTALLED + " ("
					+ ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ PACKAGE + " STRING UNIQUE NOT NULL, "
					+ RELEASE_NAME + " STRING NOT NULL, "
					+ APP_NAME + " STRING NOT NULL, "
					+ APP_VERSION + " INTEGER NOT NULL, "
					+ URL + " STRING NOT NULL, "
					+ ACTION + " INTEGER NOT NULL, "
					+ TO_BE_APPLIED + " INTEGER NOT NULL"
					+ ");");
			
			db.execSQL("CREATE TABLE " + MANAGED_PACKAGES + " ("
					+ ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ PACKAGE + " STRING UNIQUE NOT NULL, "
					+ APP_NAME + " STRING NOT NULL"
					+ ");");
			
			db.execSQL("CREATE TABLE " + PACKAGES_TO_BE_UNINSTALLED + " ("
					+ ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ PACKAGE + " STRING UNIQUE NOT NULL"
					+ ");");
		}
		
		/**
		 * Given that this is the initial version of the application, this
		 * will simply destroy any existing tables and create all new ones.
		 * 
		 * In the future this should handle upgrading any older version of the
		 * tables to the current version. 
		 */
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
		{
			Log.i(TAG, "Upgrading database from " + oldVersion + " to " + newVersion + ".");
			
			db.execSQL("DROP TABLE IF EXISTS " + PACKAGES_TO_BE_INSTALLED + ";");
			db.execSQL("DROP TABLE IF EXISTS " + MANAGED_PACKAGES + ";");
			db.execSQL("DROP TABLE IF EXISTS " + PACKAGES_TO_BE_UNINSTALLED + ";");
			onCreate(db);
		}
	}
	private OpenHelper mOpenHelper;
	
	// Used to synchronize all access to the "Packages to be installed" table.
	private static ReentrantLock packagesToBeInstalledTableLock = null;
	
	// Used to synchronize all access to the "Manged packages" table.
	private static ReentrantLock managedPackagesTableLock = null;
	
	// Used to synchronize all access to the "Packages to be uninstalled" 
	// table.
	private static ReentrantLock packagesToBeUninstalledTableLock = null;
	
	private Context mContext;
	
	/**
	 * Prepares the databases for access.
	 * 
	 * @param context The Context in which this application is running.
	 */
	public Database(Context context)
	{
		mContext = context;
		mOpenHelper = new OpenHelper(context);
		
		if(packagesToBeInstalledTableLock == null)
		{
			packagesToBeInstalledTableLock = new ReentrantLock();
		}
		
		if(managedPackagesTableLock == null)
		{
			managedPackagesTableLock = new ReentrantLock();
		}
		
		if(packagesToBeUninstalledTableLock == null)
		{
			packagesToBeUninstalledTableLock = new ReentrantLock();
		}
	}
	
	/**
	 * Creates a new update entry in the database for the package whose
	 * information is provided in the parameterized 'packageInfo'. 
	 * 
	 * Note: 'packageInfo' is not validated in any way.
	 * 
	 * @param packageInfo A PackageInformation object to which this new record
	 * 					  will be based on.
	 * 
	 * @throws SQLException Thrown if there are any serious issues within the
	 * 						database. Some of these errors include the
	 * 						inability to open the database or multiple records
	 * 						were modified when only one should be.
	 */
	public void addUpdate(PackageInformation packageInfo) throws SQLException
	{
		packagesToBeInstalledTableLock.lock();
		
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		try
		{
			Cursor c = db.query(PACKAGES_TO_BE_INSTALLED, new String[] {ID, PACKAGE}, PACKAGE + "=?", new String[] {packageInfo.getQualifiedName()}, null, null, null);
			if(c == null)
			{
				throw new SQLException("Could not query the database to see if " + packageInfo.getQualifiedName() + " already existed.");
			}
			c.moveToFirst();
			
			while(!c.isAfterLast())
			{
				db.delete(PACKAGES_TO_BE_INSTALLED, ID + "=?", new String[] {Long.toString(c.getLong(0))});
				c.moveToNext();
			}
			c.close();
			
			SharedPreferences preferences = mContext.getSharedPreferences(PACKAGE_PREFERENCES, Context.MODE_PRIVATE);
			
			ContentValues cv = new ContentValues();
			cv.put(PACKAGE, packageInfo.getQualifiedName());
			cv.put(RELEASE_NAME, packageInfo.getReleaseName());
			cv.put(APP_NAME, packageInfo.getDisplayName());
			cv.put(APP_VERSION, packageInfo.getVersion());
			cv.put(URL, packageInfo.getUrl());
			cv.put(ACTION, translateAction(packageInfo.getAction()));
			cv.put(TO_BE_APPLIED, preferences.getBoolean(PREFERENCES_MANAGED, false) ? 1 : 0);
			if(db.insert(PACKAGES_TO_BE_INSTALLED, null, cv) == -1)
			{
				throw new SQLException("Failed to insert new entry into the database.");
			}
		}
		finally
		{
			db.close();
			packagesToBeInstalledTableLock.unlock();
		}
	}
	
	/**
	 * Removes all updates for the package with the qualified name
	 * 'qualifiedName'. This should only ever be 0 or 1 packages as the
	 * database will only hold one piece of update information about a
	 * package at a time.
	 * 
	 * @param qualifiedName The qualified name of the package that should have
	 * 						its updates removed from the database.
	 * 
	 * @return Returns the number of records affected by this delete which
	 * 		   should only ever be 0 or 1 or -1 if there was an error.
	 */
	public int removeUpdate(String qualifiedName) throws InvalidParameterException
	{
		if(qualifiedName == null)
		{
			throw new InvalidParameterException("'qualifiedName' is null.");
		}
		packagesToBeInstalledTableLock.lock();
		
		int result = -1;
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		try
		{
			result = db.delete(PACKAGES_TO_BE_INSTALLED, PACKAGE + "=?", new String[] {qualifiedName});
		}
		finally
		{
			db.close();
			packagesToBeInstalledTableLock.unlock();
		}
		return result;
	}
	
	/**
	 * Removes all the updates in the database.
	 * 
	 * Caution: With great power comes great responsibility.
	 * 
	 * @return Returns the number of updates deleted or -1 if there was an
	 * 		   error.
	 */
	public int removeAllUpdates()
	{
		packagesToBeInstalledTableLock.lock();
		
		int result = -1;
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		try
		{
			result = db.delete(PACKAGES_TO_BE_INSTALLED, null, null);
		}
		finally
		{
			db.close();
			packagesToBeInstalledTableLock.unlock();
		}
		return result;
	}
	
	/**
	 * Returns all updates for the packages that are being managed as well as
	 * available packages that may not be currently be managed but can be
	 * installed and subsequently managed.
	 * 
	 * @return Returns a Cursor object that references all the packages that
	 * 		   have updates available or can be installed and subsequently
	 * 		   managed.
	 */
	public PackageInformation[] getUpdates() throws SQLException
	{
		packagesToBeInstalledTableLock.lock();
		
		PackageInformation[] result = null;
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		try
		{
			Cursor c = db.query(PACKAGES_TO_BE_INSTALLED, new String[] {PACKAGE, RELEASE_NAME, APP_NAME, APP_VERSION, URL, ACTION, TO_BE_APPLIED}, null, null, null, null, null);
			if(c == null)
			{
				throw new SQLException("Couldn't read the packages to be installed database.");
			}
			c.moveToFirst();
			
			int numRecords = c.getCount();
			result = new PackageInformation[numRecords];
			for(int i = 0; i < numRecords; i++)
			{
				result[i] = new PackageInformation(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getString(4), translateAction(c.getInt(5)));
				result[i].setToBeApplied(c.getInt(6) == 1);
				c.moveToNext();
			}
			
			c.close();
		}
		finally
		{
			db.close();
			packagesToBeInstalledTableLock.unlock();
		}
		return result;
	}
	
	/**
	 * Returns a PackageInformation object populated with the information
	 * gathered about the package whose qualified name matches the 
	 * parameterized 'qualifiedName' based on the information in the 
	 * updates table. If no such package has an update available, null is
	 * returned.
	 * 
	 * @param qualifiedName The qualified name of the package in question.
	 * 
	 * @return Returns a PackageInformation object populated with information
	 * 		   from the updates table based on the parameterized
	 * 		   'qualifiedName'. If no such package update exists, null is
	 * 		   returned.
	 * 
	 * @throws SQLException Thrown if there is a serious issue accessing the
	 * 						database.
	 */
	public PackageInformation hasUpdate(String qualifiedName) throws SQLException
	{
		packagesToBeInstalledTableLock.lock();
		
		PackageInformation result = null;
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		try
		{
			Cursor c = db.query(PACKAGES_TO_BE_INSTALLED, new String[] {ID, PACKAGE, RELEASE_NAME, APP_NAME, APP_VERSION, URL, ACTION, TO_BE_APPLIED}, PACKAGE + "=?", new String[] {qualifiedName}, null, null, null);
			if(c == null)
			{
				throw new SQLException("Cannot query the updates table.");
			}
			c.moveToFirst();
			
			if(c.getCount() >= 1)
			{
				// Prune some of the results and take the last one.
				while(!c.isLast())
				{
					db.delete(PACKAGES_TO_BE_INSTALLED, ID + "=?", new String[] {Long.toString(c.getLong(0))});
					c.moveToNext();
				}
				
				result = new PackageInformation(c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getString(5), translateAction(c.getInt(6)));
				result.setToBeApplied((c.getInt(7) == 0) ? false : true);
			}
			c.close();
		}
		finally
		{
			db.close();
			packagesToBeInstalledTableLock.unlock();
		}
		
		return result;
	}
	
	/**
	 * Updates the status on whether or not an update should be done.
	 * 
	 * @param qualifiedName The qualified name of the package for which the
	 * 						status update applies.
	 * 
	 * @param toBeApplied Whether or not this update should be done when
	 * 					  installing updates.
	 * 
	 * @throws SQLException Thrown only when the database is inaccessible.
	 */
	public void changeUpdateStatus(String qualifiedName, boolean toBeApplied) throws SQLException
	{
		packagesToBeInstalledTableLock.lock();
		
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		try
		{
			Cursor c = db.query(PACKAGES_TO_BE_INSTALLED, new String[] {ID, PACKAGE, TO_BE_APPLIED}, PACKAGE + "=?", new String[] {qualifiedName}, null, null, null);
			if(c == null)
			{
				throw new SQLException("Cannot query the updates table.");
			}
			c.moveToFirst();
			
			if(c.getCount() > 0)
			{
				ContentValues cv = new ContentValues();
				cv.put(TO_BE_APPLIED, (toBeApplied) ? 1 : 0);
				db.update(PACKAGES_TO_BE_INSTALLED, cv, ID + "=?", new String[] {Long.toString(c.getLong(0))});
			}
			
			c.close();
		}
		finally
		{
			db.close();
			packagesToBeInstalledTableLock.unlock();
		}
	}
	
	/**
	 * Returns whether or not an update should be applied.
	 * 
	 * @param qualifiedName The qualified name of the package that is being
	 * 						checked.
	 * 
	 * @return Returns true if the package is marked as needing to be
	 * 		   installed; false, otherwise.
	 * 
	 * @throws SQLException Thrown if there is an issue querying the database.
	 */
	public boolean getUpdateStatus(String qualifiedName) throws SQLException
	{
		packagesToBeInstalledTableLock.lock();
		
		boolean result = false;
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		try
		{
			Cursor c = db.query(PACKAGES_TO_BE_INSTALLED, new String[] {ID, PACKAGE, TO_BE_APPLIED}, PACKAGE + "=?", new String[] {qualifiedName}, null, null, null);
			if(c == null)
			{
				throw new SQLException("Cannot query the updates table.");
			}
			
			if(c.getCount() > 0)
			{
				c.moveToFirst();
				result = c.getInt(2) == 1; 
			}
			c.close();
		}
		finally
		{
			db.close();
			packagesToBeInstalledTableLock.unlock();
		}
		
		return result;
	}
	
	/**
	 * Adds a package to the list of packages we are managing.
	 * 
	 * @param qualifiedName The qualified name of the package in the form
	 * 						qualified.name.like.this.
	 * 
	 * @throws SQLException Thrown if there is an issue querying the database.
	 */
	public void addManaged(String qualifiedName, String appName) throws SQLException
	{
		managedPackagesTableLock.lock();
		
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		try
		{
			Cursor c = db.query(MANAGED_PACKAGES, new String[] {ID, PACKAGE}, PACKAGE + "=?", new String[] {qualifiedName}, null, null, null);
			if(c == null)
			{
				throw new SQLException("Cannot query managed packages table.");
			}
			
			if(c.getCount() == 0)
			{
				ContentValues cv = new ContentValues();
				cv.put(PACKAGE, qualifiedName);
				cv.put(APP_NAME, appName);
				db.insert(MANAGED_PACKAGES, null, cv);
			}
			c.close();
		}
		finally
		{
			db.close();
			managedPackagesTableLock.unlock();
		}
	}
	
	/**
	 * Removes all instances of the qualified name from the list of packages
	 * being managed.
	 * 
	 * Note: Wildcards are not checked or used.
	 * 
	 * @param qualifiedName The qualified name of the package in the form
	 * 						qualified.name.like.this.
	 */
	public void stopManaging(String qualifiedName) throws SQLException
	{
		managedPackagesTableLock.lock();
		
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		try
		{
			Cursor c = db.query(MANAGED_PACKAGES, new String[] {PACKAGE}, PACKAGE + "=?", new String[] {qualifiedName}, null, null, null);
			if(c == null)
			{
				throw new SQLException("Couldn't query managed packges table.");
			}
			
			if(c.getCount() > 0)
			{
				db.delete(MANAGED_PACKAGES, PACKAGE + "=?", new String[] {qualifiedName});
				addPackageToBeRemoved(qualifiedName);
			}
			
			c.close();
		}
		finally
		{
			db.close();
			managedPackagesTableLock.unlock();
		}
	}
	
	/**
	 * Checks to see if a specific package is being managed.
	 * 
	 * @param qualifiedName The qualified name of the package in the form
	 * 						qualified.name.like.this.
	 * 
	 * @return Returns true if the package is being managed; false otherwise.
	 */
	public boolean isManaged(String qualifiedName)
	{
		managedPackagesTableLock.lock();
		
		boolean result = false;
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		try
		{
			Cursor c = db.query(MANAGED_PACKAGES, new String[] {PACKAGE}, PACKAGE + "=?", new String[] {qualifiedName}, null, null, null);
			if(c == null)
			{
				throw new SQLException("Unable to query managed packages database.");
			}
			
			result = c.getCount() > 0;
			c.close();
		}
		finally
		{
			db.close();
			managedPackagesTableLock.unlock();
		}
		
		return result;
	}
	
	/**
	 * Gets the list of packages being managed.
	 * 
	 * @return Returns a list of the packages being managed.
	 * 
	 * @throws SQLException Thrown if there is an issue reading from the
	 * 						database.
	 */
	public LinkedList<String> getManaged() throws SQLException
	{
		managedPackagesTableLock.lock();
		
		LinkedList<String> result = new LinkedList<String>();
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		try
		{
			Cursor c = db.query(MANAGED_PACKAGES, new String[] {PACKAGE}, null, null, null, null, null);
			if(c == null)
			{
				throw new SQLException("Cannot query managed packages table.");
			}
			c.moveToFirst();
			
			while(!c.isAfterLast())
			{
				result.add(c.getString(0));
				c.moveToNext();
			}
			c.close();
		}
		finally
		{
			db.close();
			managedPackagesTableLock.unlock();
		}
		
		return result;
	}
	
	/**
	 * Gets PackageDescription objects for all the packages that are being 
	 * managed. 
	 * 
	 * @return A LinkedList of PackageDescription objects for each of the 
	 * 		   packages being managed. If no packages are being managed, the
	 * 		   list will be empty.
	 * 
	 * @throws SQLException Thrown if there is an issue reading from the
	 * 						database.
	 */
	public LinkedList<PackageDescription> getMangedDescriptions() throws SQLException
	{
		managedPackagesTableLock.lock();
		
		LinkedList<PackageDescription> result = new LinkedList<PackageDescription>();
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		try
		{
			Cursor c = db.query(MANAGED_PACKAGES, new String[] {PACKAGE, APP_NAME}, null, null, null, null, null);
			if(c == null)
			{
				throw new SQLException("Cannot query managed packages table.");
			}
			c.moveToFirst();
			
			while(!c.isAfterLast())
			{
				result.add(new PackageDescription(c.getString(0), c.getString(1)));
				c.moveToNext();
			}
			c.close();
		}
		finally
		{
			db.close();
			managedPackagesTableLock.unlock();
		}
		
		return result;
	}
	
	/**
	 * Adds a new package name to the list of packages that need to be
	 * removed. If the package already exists in the list, this call is
	 * ignored.
	 * 
	 * Note: This is private because we will manage when packages should be
	 * 		 removed based on when we stop managing them.
	 * 
	 * @param qualifiedName The qualified name of the package that should be
	 * 						uninstalled.
	 * 
	 * @throws SQLException Thrown if there is a serious error reading from
	 * 						the database.
	 */
	private void addPackageToBeRemoved(String qualifiedName) throws SQLException
	{
		packagesToBeUninstalledTableLock.lock();
		
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		try
		{
			Cursor c = db.query(PACKAGES_TO_BE_UNINSTALLED, new String[] {ID, PACKAGE}, PACKAGE + "=?", new String[] {qualifiedName}, null, null, null);
			if(c == null)
			{
				throw new SQLException("Couldn't query the 'packages to be uninstalled' table.");
			}
			
			if(c.getCount() == 0)
			{
				ContentValues cv = new ContentValues();
				cv.put(PACKAGE, qualifiedName);
				db.insert(PACKAGES_TO_BE_UNINSTALLED, null, cv);
			}
			c.close();
		}
		finally
		{
			db.close();
			packagesToBeUninstalledTableLock.unlock();
		}
	}
	
	/**
	 * Removes all instances of 'qualifiedName' from the list of packages
	 * that need to be uninstalled.
	 * 
	 * @param qualifiedName A fully qualified name of the package that should
	 * 						be uninstalled.
	 */
	public void removePackageToBeRemoved(String qualifiedName)
	{
		packagesToBeUninstalledTableLock.lock();
		
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		try
		{
			db.delete(PACKAGES_TO_BE_UNINSTALLED, PACKAGE + "=?", new String[] {qualifiedName});
		}
		finally
		{
			db.close();
			packagesToBeUninstalledTableLock.unlock();
		}
	}
	
	/**
	 * Returns whether or not a package, based on its qualified name, is in
	 * the list of packages to be removed. This has no bearing on whether or
	 * not the package is actually installed on the device.
	 * 
	 * @param qualifiedName The qualified name of the package in question.
	 * 
	 * @return Returns true if the package is in the list of packages to be
	 * 		   removed; false, otherwise.
	 * 
	 * @throws SQLException Thrown if there is a serious error reading from
	 * 						the database.
	 */
	public boolean isPackageToBeRemoved(String qualifiedName) throws SQLException
	{
		packagesToBeUninstalledTableLock.lock();
		
		boolean result = false;
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		try
		{
			Cursor c = db.query(PACKAGES_TO_BE_UNINSTALLED, new String[] {ID, PACKAGE}, PACKAGE + "=?", new String[] {qualifiedName}, null, null, null);
			if(c == null)
			{
				throw new SQLException("Couldn't query the 'packages to be uninstalled' table.");
			}
			
			result = c.getCount() > 0;
			c.close();
		}
		finally
		{
			db.close();
			packagesToBeUninstalledTableLock.unlock();
		}
		
		return result;
	}
	
	/**
	 * Returns a list of all packages that are currently listed as needing
	 * to be removed.
	 * 
	 * @return A LinkedList of all packages that may need to be removed.
	 */
	public LinkedList<String> getPackagesToBeRemoved()
	{
		packagesToBeUninstalledTableLock.lock();
		
		LinkedList<String> result = new LinkedList<String>();
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		try
		{
			Cursor c = db.query(PACKAGES_TO_BE_UNINSTALLED, new String[] {ID, PACKAGE}, null, null, null, null, null);
			if(c == null)
			{
				throw new SQLException("Couldn't query the 'packages to be uninstalled' table.");
			}
			c.moveToFirst();
			
			while(!c.isAfterLast())
			{
				result.add(c.getString(1));
				c.moveToNext();
			}
			c.close();
		}
		finally
		{
			db.close();
			packagesToBeUninstalledTableLock.unlock();
		}
		
		return result;
	}
	
	/**
	 * Translates the 'Action's supplied by PackageInformation objects into
	 * integer values to be stored in the database.
	 * 
	 * @param action The Action value retrieved from a PackageInformation
	 * 				 object.
	 * 
	 * @return The local integer representation of the parameterized 'action'.
	 */
	public static int translateAction(Action action)
	{
		switch(action)
		{
		case CLEAN:
			return ACTION_CLEAN;
			
		case UPDATE:
			return ACTION_UPDATE;
			
		default:
			return ACTION_CLEAN;
		}
	}
	
	/**
	 * Returns a PackageInformation 'Action' based on the parameterized
	 * 'action' that is presumed to be from the database.
	 * 
	 * @param action The database's interpretation of a PackageInformation
	 * 				 'Action'.
	 * 
	 * @return A PackageInformation 'Action' based on the parameterized
	 * 		   'action'.
	 */
	public static Action translateAction(int action)
	{
		switch(action)
		{
		case ACTION_CLEAN:
			return Action.CLEAN;
			
		case ACTION_UPDATE:
			return Action.UPDATE;
			
		default:
			return Action.CLEAN;
		}
	}
}
