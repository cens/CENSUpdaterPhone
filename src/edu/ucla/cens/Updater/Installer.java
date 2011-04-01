package edu.ucla.cens.Updater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import edu.ucla.cens.systemlog.Log;

/**
 * This Activity gives the user basic feedback on the status of the updates.
 * It is composed of two threads, a downloader and an installer, that manage
 * their respective tasks to prevent UI thread blocking.
 * 
 * @author John Jenkins
 * @version 1.0
 */
public class Installer extends Activity
{
	private static final String TAG = "CENS.Updater.Installer";
	
	private static final String INSTALL_ACTION = "edu.ucla.cens.Updater.Installer.AppInstalled";
	private static final String REINSTALL_ACTION = "edu.ucla.cens.Updater.Installer.AppReinstalled";
	
	private static final int FINISHED_INSTALLING_PACKAGE = 1;
	private static final int FINISHED_UNINSTALLING_PACKAGE = 2;
	
	private static final int FINISHED_DOWNLOADING_MESSAGE = 1;
	private static final int FINISHED_INSTALLING_MESSAGE = 2;
	private static final int UPDATE_INSTALLER_TEXT = 3;
	private static final int UPDATE_DOWNLOADER_TEXT = 4;
	private static final int UPDATE_PROGRESS_BAR = 5;
	
	private static final int MAX_CHUNK_LENGTH = 4096;
	
	private static final int PROGRESS_BAR_MAX = 100;
	
	private static final float FONT_SIZE = 18.0f;
	
	private static final String TEXT_INITIAL = "Getting package list";
	private static final String TEXT_NO_PACKAGES = "No packages available for updating";
	private static final String TEXT_DATABASE_ERROR = "Internal error while retrieving package list";
	
	private Context mContext;
	
	private TextView installerText;
	private TextView downloaderText;
	private ProgressBar progressBar;
	
	private PackageDownloader downloaderThread;
	private PackageInstaller installerThread;
	
	private PackageInformation[] packagesToBeUpdated;
	private int currPackageIndex;
	private boolean currPackageReinstall;
	
	private boolean currPackageError;
	private boolean installerKilled;
	
	private String newInstallerText;
	private String newDownloaderText;
	private int newProgressBarValue;
	
	/**
	 * Private class that downloads the current file and sends a message back
	 * to the UI thread when complete.
	 * 
	 * @author John Jenkins
	 * @version 1.0
	 */
	private class PackageDownloader implements Runnable
	{
		private boolean killed = false;
		private int totalLength = 0;
		
		/**
		 * Downloads the current packages based on the shared variable's 
		 * values. Sends a signal via the Handler when complete.
		 */
		@Override
		public void run()
		{
			updateInstallerText("Downloading " + packagesToBeUpdated[currPackageIndex].getDisplayName());
			
			URLConnection connection;
			InputStream dataStream;
			byte[] buff;
			
			if(killed) return;
			
			URL url;
			try
			{
				url = new URL(packagesToBeUpdated[currPackageIndex].getUrl());
			}
			catch(MalformedURLException e)
			{
				error("Malformed URL in package " + packagesToBeUpdated[currPackageIndex].getQualifiedName(), e);
				return;
			}
			
			if(killed) return;
			
			try
			{
				connection = url.openConnection();
				totalLength = connection.getContentLength();
			}
			catch(IOException e)
			{
				error("Failed to connect to the remote file.", e);
				return;
			}
			
			if(killed) return;
			
			try
			{
				dataStream = connection.getInputStream();
			}
			catch(IOException e)
			{
				error("Failed to open an input stream from the url: " + url, e);
				return;
			}
			
			if(killed) return;
			
			FileOutputStream apkFile;
			try
			{
				apkFile = openFileOutput(packagesToBeUpdated[currPackageIndex].getQualifiedName() + ".apk", MODE_WORLD_READABLE);
			}
			catch(IllegalArgumentException e)
			{
				error("The package filename was invalid.", e);
				return;
			}
			catch(IOException e)
			{
				error("Could not create temporary file.", e);
				return;
			}
			
			if(killed) return;
			
			try
			{
				int totalDownloaded = 0;
				int chunkSize = 0;
				int remaining = totalLength - totalDownloaded;
				if(remaining <= 0)
				{
					throw new IOException("Invalid file length: " + totalLength);
				}
				
				buff = new byte[MAX_CHUNK_LENGTH];
				while((chunkSize = dataStream.read(buff)) != -1)
				{
					try
					{
						if(killed) 
						{
							cleanUp();
							return;
						}
						
						apkFile.write(buff, 0, chunkSize);
						
						totalDownloaded += chunkSize;
						updateProgressBarValue(totalDownloaded);
						
						remaining = totalLength - totalDownloaded;
						if(remaining < 0)
						{
							Log.e(TAG, "Downloaded more than the total size of the file.");
							remaining = 0;
						}
					}
					catch(IOException e)
					{
						error("Error while writing to the file output stream.", e);
						return;
					}
				}
			}
			catch(IOException e)
			{
				error("Error while reading from the url input stream.", e);
				return;
			}
			
			if(killed) 
			{
				cleanUp();
				return;
			}
			
			messageHandler.sendMessage(messageHandler.obtainMessage(FINISHED_DOWNLOADING_MESSAGE));
		}
		
		/**
		 * Sets the state of this thread to be killed which is checked at
		 * regular intervals. Once the kill flag is detected, it will kill the
		 * thread.
		 */
		public void kill()
		{
			killed = true;
		}
		
		/**
		 * Called whenever an error takes place to log it, update the UI, set
		 * the appropriate shared variables, and send a message back that it
		 * was finished.
		 * 
		 * @param error A String representing the error that occurred.
		 * 
		 * @param e The Exception thrown by the system.
		 */
		private void error(String error, Exception e)
		{
			Log.e(TAG, error, e);
			updateInstallerText("Error while downloading " + packagesToBeUpdated[currPackageIndex].getDisplayName());
			currPackageError = true;
			messageHandler.sendMessage(messageHandler.obtainMessage(FINISHED_DOWNLOADING_MESSAGE));
		}
		
		/**
		 * Deletes any file that may exist as a result of us being killed.
		 */
		private void cleanUp()
		{
			try
			{
				(new File(getApplicationContext().getFilesDir().getAbsolutePath() + "/" + packagesToBeUpdated[currPackageIndex].getQualifiedName() + ".apk")).delete();
			}
			catch(Exception e)
			{
				// I recognize that it is bad to catch a generic 
				// Exception, but in this case I don't care what happens.
				// I just want to fall out.
			}
		}
		
		/**
		 * Updates the shared variable for what String should be displayed in
		 * the status title and sends a message back to the UI thread to
		 * refresh the text.
		 * 
		 * @param text The text to be shown in the status title.
		 */
		private void updateInstallerText(String text)
		{
			newInstallerText = text;
			messageHandler.sendMessage(messageHandler.obtainMessage(UPDATE_INSTALLER_TEXT));
		}

		/**
		 * Updates the progress bar and the text below the progress bar with
		 * the parameterized values.
		 * 
		 * @param totalDownloaded The value quantity downloaded thus far.
		 */
		private void updateProgressBarValue(int totalDownloaded)
		{
			newProgressBarValue = (totalDownloaded / totalLength) * PROGRESS_BAR_MAX;
			newDownloaderText = totalDownloaded + " / " + totalLength;
			
			messageHandler.sendMessage(messageHandler.obtainMessage(UPDATE_PROGRESS_BAR));
			messageHandler.sendMessage(messageHandler.obtainMessage(UPDATE_DOWNLOADER_TEXT));
		}
	}
	
	/**
	 * Checks to make sure no error had previously occurred and, if not,
	 * starts the Android installer with the package we just downloaded. 
	 * 
	 * @author John Jenkins
	 * @version 1.0
	 */
	private class PackageInstaller implements Runnable
	{
		private boolean killed = false;
		
		/**
		 * Checks to make sure no error had occurred and then starts the
		 * Android installer with the information just given.
		 */
		@Override
		public void run()
		{
			if(currPackageError)
			{
				Log.e(TAG, "Aborting installer for " + packagesToBeUpdated[currPackageIndex].getQualifiedName());
				messageHandler.sendMessage(messageHandler.obtainMessage(FINISHED_INSTALLING_MESSAGE));
				return;
			}
			else if(installerKilled)
			{
				cleanUp();
				return;
			}
			
			updateInstallerText("Installing " + packagesToBeUpdated[currPackageIndex].getDisplayName());
			
			if(killed) 
			{
				cleanUp();
				return;
			}
			
			File apkFile = new File(getApplicationContext().getFilesDir().getAbsolutePath() + "/" + packagesToBeUpdated[currPackageIndex].getQualifiedName() + ".apk");
			if(apkFile.exists())
			{
				if(killed) 
				{
					cleanUp();
					return;
				}
				
				if(packagesToBeUpdated[currPackageIndex].getQualifiedName().equals("edu.ucla.cens.Updater"))
				{
					SharedPreferences sharedPreferences = mContext.getSharedPreferences(Database.PACKAGE_PREFERENCES, Context.MODE_PRIVATE);
					sharedPreferences.edit().putBoolean(Database.PREFERENCES_SELF_UPDATE, true).commit();
				}
				
				Intent installIntent = new Intent(android.content.Intent.ACTION_VIEW);
				installIntent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
				startActivityForResult(installIntent, FINISHED_INSTALLING_PACKAGE);
			}
			else
			{
				Log.e(TAG, "File does not exist.");
				messageHandler.sendMessage(messageHandler.obtainMessage(FINISHED_INSTALLING_MESSAGE));
			}
		}
		
		/**
		 * Sets the state of this thread to be killed which is checked at
		 * regular intervals. Once the kill flag is detected, it will kill the
		 * thread.
		 */
		public void kill()
		{
			killed = true;
		}
		
		/**
		 * Deletes any file that may exist as a result of us being killed.
		 */
		private void cleanUp()
		{
			try
			{
				(new File(getApplicationContext().getFilesDir().getAbsolutePath() + "/" + packagesToBeUpdated[currPackageIndex].getQualifiedName() + ".apk")).delete();
			}
			catch(Exception e)
			{
				// I recognize that it is bad to catch a generic 
				// Exception, but in this case I don't care what happens.
				// I just want to fall out.
			}
		}
		
		/**
		 * Updates the shared variable for what String should be displayed in
		 * the status title and sends a message back to the UI thread to
		 * refresh the text.
		 * 
		 * @param text The text to be shown in the status title.
		 */
		private void updateInstallerText(String text)
		{
			newInstallerText = text;
			messageHandler.sendMessage(messageHandler.obtainMessage(UPDATE_INSTALLER_TEXT));
		}
	}
	
	/**
	 * Handles messages sent by the local Threads such as updating the text
	 * and signaling completion of an activity.
	 */
	private Handler messageHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			if(msg.what == FINISHED_DOWNLOADING_MESSAGE)
			{
				progressBar.setProgress(0);
				installerThread = new PackageInstaller();
				Thread installer = new Thread(installerThread);
				installer.setName("Installer");
				installer.start();
			}
			else if(msg.what == FINISHED_INSTALLING_MESSAGE)
			{
				nextPackage();
			}
			else if(msg.what == UPDATE_INSTALLER_TEXT)
			{
				installerText.setText(newInstallerText);
			}
			else if(msg.what == UPDATE_DOWNLOADER_TEXT)
			{
				downloaderText.setText(newDownloaderText);
			}
			else if(msg.what == UPDATE_PROGRESS_BAR)
			{
				progressBar.setProgress(newProgressBarValue);
			}
		}
	};

	/**
	 * Sets up the UI and then checks to ensure that updates need to be done.
	 * If not, it aborts the Activity; if so, it begins checking packages.
	 */
	@Override
	public void onCreate(Bundle savedInstance)
	{
		super.onCreate(savedInstance);
		setContentView(R.layout.installer);
		
		Log.initialize(this, Database.LOGGER_APP_NAME);
		
		if(Settings.Secure.getInt(getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 0)
		{
			Toast.makeText(this, "Please enable the installation of non-market apps before continuing. Thank you.", Toast.LENGTH_LONG).show();
			Intent intent = new Intent();
		    intent.setAction(Settings.ACTION_APPLICATION_SETTINGS);
		    startActivity(intent);
		    
		    finish();
		    return;
		}
		
		mContext = this;
		
		((LinearLayout) findViewById(R.id.installer_layout)).setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
		
		downloaderText = ((TextView) findViewById(R.id.download_text));
		downloaderText.setGravity(Gravity.CENTER_HORIZONTAL);
		
		installerText = (TextView) findViewById(R.id.installer_text);
		installerText.setTextSize(FONT_SIZE);
		installerText.setText(TEXT_INITIAL);
		installerText.setGravity(Gravity.CENTER_HORIZONTAL);
		
		progressBar = (ProgressBar) findViewById(R.id.progress_bar);
		progressBar.setMax(PROGRESS_BAR_MAX);
		
		Database db = new Database(this);
		packagesToBeUpdated = db.getUpdates();
		
		if(packagesToBeUpdated == null)
		{
			Log.e(TAG, "List of packages to be updated is null.");
			installerText.setText(TEXT_DATABASE_ERROR);
			finish();
			return;
		}
		else if(packagesToBeUpdated.length <= 0)
		{
			Log.i(TAG, "List of packages has no updates in it.");
			installerText.setText(TEXT_NO_PACKAGES);
			finish();
			return;
		}
		else
		{
			installerKilled = false;
			currPackageIndex = 0;
			processPackage();
		}
	}
	
	/**
	 * If we are calling finish() on this Activity, we need to stop the
	 * downloader and installer threads to make sure that we are forcibly
	 * stopping everything.
	 * 
	 * If this Activity is being destroyed by the system, we need to save the
	 * state of the threads and the installer to prevent accidentally skipping
	 * a package.
	 */
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		
		installerKilled = true;
		
		if(isFinishing())
		{
			if(downloaderThread != null)
			{
				try
				{
					downloaderThread.kill();
				}
				catch(SecurityException e)
				{
					Log.e(TAG, "Could not stop downloader thread.", e);
				}
			}
			
			if(installerThread != null)
			{
				try
				{
					installerThread.kill();
				}
				catch(SecurityException e)
				{
					Log.e(TAG, "Could not stop installer thread.", e);
				}
			}
		}
		else
		{
			// TODO: The system is killing this process. We need to save some
			// state so when this Activity is recreated we know which package
			// we were working on and don't accidentally skip a package.
		}
	}
	
	/**
	 * Called by the Android installer when an installation completes. The
	 * parameters of this function are always the same when the installation
	 * completes despite if the installation failed, was aborted, or was 
	 * successful.
	 * 
	 * To ensure that the package was successful, we check what the system has
	 * as a current version to what the update claims the new package should
	 * be. If they are the same, it considers the installation a success;
	 * otherwise, it considers it a failure. Only if it was a success will it
	 * remove the update from the database.
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if(requestCode == FINISHED_INSTALLING_PACKAGE)
		{
			try
			{
				if(packagesToBeUpdated[currPackageIndex].getQualifiedName().equals("edu.ucla.cens.Updater"))
				{
					SharedPreferences sharedPreferences = getSharedPreferences(Database.PACKAGE_PREFERENCES, Context.MODE_PRIVATE);
					sharedPreferences.edit().putBoolean(Database.PREFERENCES_SELF_UPDATE, false).commit();
					
					Log.i(TAG, "Self-update failed.");
					messageHandler.sendMessage(messageHandler.obtainMessage(FINISHED_INSTALLING_MESSAGE));
					return;
				}
				
				PackageManager packageManager = this.getPackageManager();
				PackageInfo packageInfo = packageManager.getPackageInfo(packagesToBeUpdated[currPackageIndex].getQualifiedName(), 0);
				
				if(packageInfo.versionCode == packagesToBeUpdated[currPackageIndex].getVersion())
				{	
					Database db = new Database(this);
					db.removeUpdate(packagesToBeUpdated[currPackageIndex].getQualifiedName());
					
					Intent installedPackageBroadcast = new Intent((currPackageReinstall) ? REINSTALL_ACTION : INSTALL_ACTION);
					Uri installPackageData = (new Uri.Builder()).scheme("package").authority(packagesToBeUpdated[currPackageIndex].getQualifiedName()).build();
					installedPackageBroadcast.setData(installPackageData);
					sendBroadcast(installedPackageBroadcast);
				}
				else
				{
					Log.w(TAG, "The package failed to be upgraded.");
				}
			}
			catch(NameNotFoundException e)
			{
				Log.w(TAG, "The package failed to be installed.");
			}
			
			messageHandler.sendMessage(messageHandler.obtainMessage(FINISHED_INSTALLING_MESSAGE));
		}
		else if(requestCode == FINISHED_UNINSTALLING_PACKAGE)
		{	
			PackageManager packageManager = getPackageManager();
			try
			{
				packageManager.getPackageInfo(packagesToBeUpdated[currPackageIndex].getQualifiedName(), 0);
				
				// The user didn't actually uninstall the package.
				Log.i(TAG, "The user did not uninstall the package.");
				nextPackage();
			}
			catch(NameNotFoundException e)
			{
				// Spawn a new downloader thread and start it.
				downloaderThread = new PackageDownloader();
				Thread downloader = new Thread(downloaderThread);
				downloader.setName("Downloader");
				downloader.start();
			}
		}
	}
	
	/**
	 * Attempts to delete any existing APK that may have been downloaded
	 * before moving onto the next package.
	 */
	private void nextPackage()
	{
		try
		{
			File apkFile = new File(getApplicationContext().getFilesDir().getAbsolutePath() + "/" + packagesToBeUpdated[currPackageIndex].getQualifiedName() + ".apk");
			apkFile.delete();
		}
		catch(SecurityException e)
		{
			// There is no reason I shouldn't be able to delete this file,
			// so this may be indicative of a larger problem.
			Log.e(TAG, "Failed to delete temporary APK: " + packagesToBeUpdated[currPackageIndex].getQualifiedName() + ".apk", e);
		}
		catch(ArrayIndexOutOfBoundsException e)
		{
			// Happened once when the Android Installer said it couldn't parse
			// the package and then started the Installer anyway.
			Log.e(TAG, "Trying to delete a file when the package index has exceeded the length of the number of packages.");
		}
		
		currPackageIndex++;
		processPackage();
	}
	
	/**
	 * If we have processed all packages, it will delete the temporary APK 
	 * being used to download the packages then quit. If not, it will reset
	 * the local variables and start the downloader thread for the next
	 * package.
	 */
	private void processPackage()
	{
		if(currPackageIndex >= packagesToBeUpdated.length)
		{
			Log.i(TAG, "Done updating all packages.");
			finish();
			return;
		}
		
		if(packagesToBeUpdated[currPackageIndex].getToBeApplied())
		{
			currPackageError = false;
			
			// Check to see if the package was already installed for purposes
			// of reporting in the broadcast.
			PackageManager packageManager = getPackageManager();
			try
			{
				packageManager.getPackageInfo(packagesToBeUpdated[currPackageIndex].getQualifiedName(), 0);
				
				if(packagesToBeUpdated[currPackageIndex].getAction() == PackageInformation.Action.UPDATE)
				{
					currPackageReinstall = true;
					
					// Spawn a new downloader thread and start it.
					downloaderThread = new PackageDownloader();
					Thread downloader = new Thread(downloaderThread);
					downloader.setName("Downloader");
					downloader.start();
				}
				else
				{
					currPackageReinstall = false;
					
					Uri packageUri = Uri.parse("package:" + packagesToBeUpdated[currPackageIndex].getQualifiedName());
					Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageUri);
					startActivityForResult(uninstallIntent, FINISHED_UNINSTALLING_PACKAGE);
				}
			}
			catch(NameNotFoundException e)
			{
				currPackageReinstall = false;
				
				// Spawn a new downloader thread and start it.
				downloaderThread = new PackageDownloader();
				Thread downloader = new Thread(downloaderThread);
				downloader.setName("Downloader");
				downloader.start();
			}
			
		}
		else
		{
			nextPackage();
		}
	}
}