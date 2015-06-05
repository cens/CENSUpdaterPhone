package edu.ucla.cens.Updater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import edu.ucla.cens.Updater.PackageInformation.Action;
import edu.ucla.cens.Updater.model.StatusModel;
import edu.ucla.cens.Updater.utils.AppInfoCache;
import edu.ucla.cens.Updater.utils.AppManager;
import edu.ucla.cens.Updater.utils.PMCLI;
import edu.ucla.cens.Updater.utils.OnInstalledPackage;
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
	
	private static final int FINISHED_INSTALLING_PACKAGE = 1;
	private static final int FINISHED_UNINSTALLING_PACKAGE = 2;
	
	private static final int MESSAGE_FINISHED_DOWNLOADING = 1;
	private static final int MESSAGE_FINISHED_INSTALLING = 2;
	private static final int MESSAGE_UPDATE_INSTALLER_TEXT = 3;
	private static final int MESSAGE_UPDATE_DOWNLOADER_TEXT = 4;
	private static final int MESSAGE_UPDATE_PROGRESS_BAR = 5;
	private static final int MESSAGE_FINISHED_INITIAL_CLEANUP = 6; 
	private static final int MESSAGE_FINISHED_UNINSTALLING = 7;
	
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
	//private PackageUninstaller uninstallerThread;
	
	private PackageInformation[] packagesToBeUpdated;
	private int currPackageIndex;
	
	private boolean currPackageError;
	private boolean activityKilled;
	
	private String newInstallerText;
	private String newDownloaderText;
	private int newProgressBarValue;
	
	private StatusModel model = StatusModel.get();
	
	/**
	 * Private class that downloads the current file and sends a message back
	 * to the UI thread when complete.
	 * 
	 * @author John Jenkins
	 */
	private class PackageDownloader implements Runnable
	{	
		/**
		 * Downloads the current package and stores it in the shared local
		 * directory as world readable.
		 */
		@Override
		public void run()
		{
			updateInstallerText("Downloading " + packagesToBeUpdated[currPackageIndex].getDisplayName());

			// These are placed throughout the code as a way to signal that the
			// process should stop, but without leaving the JVM or anything
			// else in a half-open state. 
			if(activityKilled) return;
			
			// Get the URL for the current package.
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
			
			if(activityKilled) return;
			
			// Open the connection to the current package and get its length.
            HttpURLConnection connection;
            int totalLength, alreadyDownloaded;
            String lastModified, responseCode;

            // Shared preferences used to store size of the apk downloaded
            // and its last modified date.
            SharedPreferences sharedPreferences = mContext.getSharedPreferences(Database.PACKAGE_PREFERENCES, Context.MODE_PRIVATE);
            try
            {
                alreadyDownloaded = sharedPreferences.getInt("alreadyDownloaded",0);
                lastModified = sharedPreferences.getString("lastmodified", "");
                connection = (HttpURLConnection) url.openConnection();

                // If this apk is partially downloaded, then ask for the rest of the apk.
                if(alreadyDownloaded > 0){
                    connection.setRequestProperty("Range", "bytes=" + alreadyDownloaded + "-");
                    connection.setRequestProperty("If-Range", lastModified);
                    connection.connect();
                }
                // If this is a new download, then store the last modified for future use.
                else{
                    connection.connect();
                    lastModified = connection.getHeaderField("Last-Modified");
                    alreadyDownloaded = 0;
                }
                // Total length of the file is length of the file being
                // downloaded + length which is already downloaded.
                totalLength = connection.getContentLength() +alreadyDownloaded;
                responseCode = String.valueOf(connection.getResponseCode());

                if(totalLength <= 0)
                {
                    error("The total length of the file is invalid: " + totalLength, new IllegalStateException("The file no longer exists or has an invalid size."));
                    Updater updater = new Updater(mContext);
                    updater.doUpdate();
                    return;
                }
            }
            catch(IOException e)
            {
                error("Failed to connect to the remote file.", e);
                return;
            }

            if(activityKilled) return;

            // Get the input stream to begin reading the content.
            InputStream dataStream;
            try
            {
                dataStream = connection.getInputStream();
            }
            catch(IOException e)
            {
                error("Failed to open an input stream from the url: " + url, e);
                return;
            }

            if(activityKilled) return;

            // Create a connection to the local file that will store the APK.
            // The package is made world readable, so that Android's package
            // installer can read it.
            FileOutputStream apkFile;
            try
            {
                // If partial content, then append the file. Else, write as usual.
                if(responseCode.equals("206")){
                    apkFile = openFileOutput(packagesToBeUpdated[currPackageIndex].getQualifiedName() + ".apk",  MODE_WORLD_READABLE | MODE_APPEND);
                }
                else{
                    apkFile = openFileOutput(packagesToBeUpdated[currPackageIndex].getQualifiedName() + ".apk",  MODE_WORLD_READABLE);

                }
            }
            catch(ArrayIndexOutOfBoundsException e)
            {
                error("The array index, " + currPackageIndex + ", was out of bounds for the packages to be updated array which length: " + packagesToBeUpdated.length, e);
                return;
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

            if(activityKilled) return;
            int totalDownloaded = 0;
            try
            {
                int currDownloaded = 0;
                totalDownloaded = alreadyDownloaded;

                // Download the file chunk by chunk each time updating the
                // interface with our progress.
                byte[] buff = new byte[MAX_CHUNK_LENGTH];
                while((currDownloaded = dataStream.read(buff)) != -1)
                {
                    try
                    {
                        if(activityKilled){
                            //If activity is killed, the store the values in shared preferences.
                            alreadyDownloaded = totalDownloaded;
                            sharedPreferences.edit().putInt("alreadyDownloaded", alreadyDownloaded).commit();
                            sharedPreferences.edit().putString("lastmodified", lastModified).commit();

                            dataStream.close();
                            return;
                        }

                        apkFile.write(buff, 0, currDownloaded);

                        totalDownloaded += currDownloaded;

                        updateProgressBarValue(totalDownloaded, totalLength);

                        // This was originally being done to debug the code but
                        // is being left in as a flag that something odd has
                        // happened.
                        if(totalLength - totalDownloaded < 0)
                        {
                            Log.e(TAG, "Downloaded more than the total size of the file.");
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
                // If IO Exception, store current downloaded and last modified
                // in shared preferences.
                alreadyDownloaded = totalDownloaded;
                sharedPreferences.edit().putInt("alreadyDownloaded", alreadyDownloaded).commit();
                sharedPreferences.edit().putString("lastmodified", lastModified).commit();

                error("Error while reading from the url input stream.", e);
                return;
            }
			
			if(activityKilled) return;

            // Reset the values in shared preferences if the download is successful.
            sharedPreferences.edit().putInt("alreadyDownloaded", 0).commit();
            sharedPreferences.edit().putString("lastmodified", "").commit();

            messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_FINISHED_DOWNLOADING));
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
			messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_FINISHED_DOWNLOADING));
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
			messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_UPDATE_INSTALLER_TEXT));
		}

		/**
		 * Updates the progress bar and the text below the progress bar with
		 * the parameterized values.
		 * 
		 * @param totalDownloaded The value quantity downloaded thus far.
		 * 
		 * @param totalLength The total length of the file we are downloading.
		 */
		private void updateProgressBarValue(int totalDownloaded, int totalLength)
		{
			newProgressBarValue = (totalDownloaded / totalLength) * PROGRESS_BAR_MAX;
			newDownloaderText = totalDownloaded + " / " + totalLength;
			
			messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_UPDATE_PROGRESS_BAR));
			messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_UPDATE_DOWNLOADER_TEXT));
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
		/**
		 * Checks to make sure no error had occurred and then starts the
		 * Android installer with the information just given.
		 */
		@Override
		public void run()
		{
			//Log.d(TAG, "PackageInstaller.run.1");

			// If the downloader failed, but we still arrived here, just fall
			// out with an error message.
			if(currPackageError)
			{
				Log.e(TAG, "Aborting installer for " + packagesToBeUpdated[currPackageIndex].getQualifiedName());
				messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_FINISHED_INSTALLING));
				return;
			}
			
			if(activityKilled) return;
			
			updateInstallerText("Installing " + packagesToBeUpdated[currPackageIndex].getDisplayName());
			
			if(activityKilled) return;
			
			String apkpath = getApplicationContext().getFilesDir().getAbsolutePath() + "/" + packagesToBeUpdated[currPackageIndex].getQualifiedName() + ".apk";
			String packageName = packagesToBeUpdated[currPackageIndex].getQualifiedName();
			File apkFile = new File(apkpath);
			//Log.d(TAG, "PackageInstaller.run.2: " + apkFile);
			if(apkFile.exists())
			{
				if(activityKilled) return;
				
				if(packagesToBeUpdated[currPackageIndex].getQualifiedName().equals("edu.ucla.cens.Updater"))
				{
					SharedPreferences sharedPreferences = mContext.getSharedPreferences(Database.PACKAGE_PREFERENCES, Context.MODE_PRIVATE);
					sharedPreferences.edit().putBoolean(Database.PREFERENCES_SELF_UPDATE, true).commit();
				}
				
				
				/*
				Intent installIntent = new Intent(android.content.Intent.ACTION_VIEW);
				installIntent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
				
				
				startActivityForResult(installIntent, FINISHED_INSTALLING_PACKAGE);
				*/
				Action action = packagesToBeUpdated[currPackageIndex].getAction();
				PMCLI am;
				Log.d(TAG, "am starting istall: " + apkpath);
				try {
					am = new PMCLI(getApplicationContext());
					am.setOnInstalledPackaged(new OnInstalledPackage() {
						 
					    public void packageInstalled(String packageName, int returnCode, String message) {
					    	String msg;
					        if (returnCode == PMCLI.INSTALL_SUCCEEDED) {
					        	msg = "Install succeeded for " + packageName + ": " + message;
					            Log.d(TAG, msg);
								//updateInstallerText("Installed " + packagesToBeUpdated[currPackageIndex].getDisplayName());
								updateInstallerText("Installed " + packageName);
								model.addInfoMessage(msg);
					        } else {
					        	msg = "Install failed for " + packageName + ": rc=" + returnCode + ": " + message;
					            Log.e(TAG, msg);
								//updateInstallerText("Installed " + packagesToBeUpdated[currPackageIndex].getDisplayName());
								updateInstallerText("Failed to install " + packageName);
								model.addErrorMessage(msg);
					        }
					        // do async toast to run on ui thread
				        	AppManager.get().doToastMessageAsync(msg);
							messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_FINISHED_INSTALLING));
					    }
					    
					    public void packageUninstalled(String packageName, int returnCode, String message) {
					    	String msg;
					        if (returnCode == PMCLI.UNINSTALL_SUCCEEDED) {
					        	msg = "Uninstall succeeded for " + packageName + ": " + message;
					            Log.d(TAG, msg);
								//updateInstallerText("Installed " + packagesToBeUpdated[currPackageIndex].getDisplayName());
								updateInstallerText("Uninstalled " + packageName);
								model.addInfoMessage(msg);
								messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_FINISHED_UNINSTALLING));				        	
					        } else {
					        	msg = "Uninstall failed for " + packageName + ": rc=" + returnCode + ": " + message;
					            Log.e(TAG, msg);
								//updateInstallerText("Installed " + packagesToBeUpdated[currPackageIndex].getDisplayName());
								updateInstallerText("Failed to uninstall " + packageName);
								model.addErrorMessage(msg);
					        }
					        // do async toast to run on ui thread
				        	AppManager.get().doToastMessageAsync(msg);
							messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_FINISHED_INSTALLING));				        	
					    }
					    
					});
					if (action == Action.UPDATE) {
						am.installPackageViaShell(apkpath);
					} else if (action == Action.CLEAN) {
						// if package not installed any more, install it now
						try {
							// Get the package's information. If this doesn't throw an
							// exception, then the package must be installed.
							PackageManager packageManager = getPackageManager();
							packageManager.getPackageInfo(packageName, 0);
							// no exception: let's uninstall it
							am.uninstallPackageViaShell(packageName);
						} catch(NameNotFoundException e) {
							// The package isn't yet installed.
							am.installPackageViaShell(apkpath);
						}						
					} else {
						throw new RuntimeException("Only Actions UPDATE and CLEAN uspported");
					}
				} catch (SecurityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchMethodException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
				}
				
			}
			else
			{
				Log.e(TAG, "File does not exist.");
				messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_FINISHED_INSTALLING));
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
			messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_UPDATE_INSTALLER_TEXT));
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
			//Log.d(TAG, "handleMessage: " + msg);
			if(msg.what == MESSAGE_FINISHED_DOWNLOADING)
			{
				progressBar.setProgress(0);
				installerThread = new PackageInstaller();
				Thread installer = new Thread(installerThread);
				installer.setName("Installer");
				installer.start();
			}
			else if(msg.what == MESSAGE_FINISHED_UNINSTALLING)
			{
				progressBar.setProgress(0);
				installerThread = new PackageInstaller();
				Thread installer = new Thread(installerThread);
				installer.setName("Installer");
				installer.start();
			}
			else if(msg.what == MESSAGE_FINISHED_INSTALLING)
			{
				nextPackage();
			}
			else if(msg.what == MESSAGE_UPDATE_INSTALLER_TEXT)
			{
				installerText.setText(newInstallerText);
			}
			else if(msg.what == MESSAGE_UPDATE_DOWNLOADER_TEXT)
			{
				downloaderText.setText(newDownloaderText);
			}
			else if(msg.what == MESSAGE_UPDATE_PROGRESS_BAR)
			{
				progressBar.setProgress(newProgressBarValue);
			}
			else if(msg.what == MESSAGE_FINISHED_INITIAL_CLEANUP)
			{
				processPackage();
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
			activityKilled = false;
			currPackageIndex = 0;
            processPackage();
		}
	}
	
	/**
	 * If the Activity is dying, we need to set the appropriate flag so that
	 * any of the running threads will also die.
	 * 
	 * If this Activity is being destroyed by the system, we need to save the
	 * state of the threads and the installer to prevent accidentally skipping
	 * a package.
	 */
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		
		activityKilled = true;

		if(! isFinishing())
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
					messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_FINISHED_INSTALLING));
					return;
				}
				
				PackageManager packageManager = this.getPackageManager();
				PackageInfo packageInfo = packageManager.getPackageInfo(packagesToBeUpdated[currPackageIndex].getQualifiedName(), 0);
				
				if(packageInfo.versionCode == packagesToBeUpdated[currPackageIndex].getVersion())
				{	
					Database db = new Database(this);
					db.removeUpdate(packagesToBeUpdated[currPackageIndex].getQualifiedName());
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
			
			messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_FINISHED_INSTALLING));
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
	 * Increases our index and begins to process the next package.
	 */
	private void nextPackage()
	{
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
		// If we have processed all packages, leave.
		if(currPackageIndex >= packagesToBeUpdated.length)
		{
			Log.i(TAG, "Done updating all packages.");
			// refresh app info cache 
			AppInfoCache.get().refresh();
			AppManager.get().nototifyMainActivity();
			finish();
			return;
		}
		
		// We need to check if the update should actually be applied.
		if(packagesToBeUpdated[currPackageIndex].getToBeApplied())
		{
			currPackageError = false;
			
			// Check to see if the package was already installed for purposes
			// of reporting in the broadcast.
			PackageManager packageManager = getPackageManager();
			try
			{
				// Get the package's information. If this doesn't throw an
				// exception, then the package must be installed.
				packageManager.getPackageInfo(packagesToBeUpdated[currPackageIndex].getQualifiedName(), 0);
				
				// If the package is to be updated,
				if(packagesToBeUpdated[currPackageIndex].getAction().equals(PackageInformation.Action.UPDATE))
				{
					// Spawn a new downloader thread and start it.
					downloaderThread = new PackageDownloader();
					Thread downloader = new Thread(downloaderThread);
					downloader.setName("Downloader");
					downloader.start();
				}
				// Otherwise, the package must be installed but it isn't an
				// update, so we need to first remove the original package.
				else
				{
					// we do the same thing for action.CLEAN for now.
					// that will trigger installer after download which will do the right thing:
					//   it will first run uninstall, send an MESSAGE_FINISHED_UNINSTALLING message to handler
					//   which will then trigger install
					downloaderThread = new PackageDownloader();
					Thread downloader = new Thread(downloaderThread);
					downloader.setName("Downloader");
					downloader.start();
					/*
					 * original code called system intent to uninstall with user interaction
					//Uri packageUri = Uri.parse("package:" + packagesToBeUpdated[currPackageIndex].getQualifiedName());
					//Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageUri);
					//startActivityForResult(uninstallIntent, FINISHED_UNINSTALLING_PACKAGE);
					*/
				}
			}
			// The package isn't yet installed.
			catch(NameNotFoundException e)
			{
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