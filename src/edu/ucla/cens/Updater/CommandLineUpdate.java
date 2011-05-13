package edu.ucla.cens.Updater;

import java.io.DataOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import edu.ucla.cens.systemlog.Log;

public class CommandLineUpdate extends Activity
{
	private static final String TAG = "CENS.Updater.CommandLineUpdate";
	
	@Override
	public void onCreate(Bundle savedInstance)
	{
		super.onCreate(savedInstance);
		
		Process process;
		try
		{
			process = Runtime.getRuntime().exec("su");
		}
		catch(SecurityException e)
		{
			Log.e(TAG, "The SecurityManager dissallows program execution.", e);
			finish();
			return;
		}
		catch(IOException e)
		{
			Log.e(TAG, "Could not execute 'su', so it either isn't installed or there was a patch that broke it.", e);
			finish();
			return;
		}
		
		String pathToFile;
		Uri theData = getIntent().getData();
		if(theData.getScheme().equals("file"))
		{
			pathToFile = theData.getPath();
		}
		else
		{
			Log.e(TAG, "The 'data' in the Intent associated with this call must be an Android Uri that is based on a file on this device.");
			finish();
			return;
		}
		
		DataOutputStream suCommandLine = new DataOutputStream(process.getOutputStream());
		try
		{
			suCommandLine.writeBytes("pm install -r " + pathToFile + "; exit\n");
			suCommandLine.flush();
		}
		catch(IOException e)
		{
			Log.e(TAG, "Error executing install command: pm install -r " + pathToFile, e);
			finish();
			return;
		}
		
		try
		{	
			process.waitFor();
			if(process.exitValue() != 255)
			{
				Log.i(TAG, "Installation completed with exit value: " + process.exitValue());
				finish();
			}
			else
			{
				Log.i(TAG, "Installation failed with exit value: " + process.exitValue());
				finish();
			}
		}
		catch(InterruptedException e)
		{
			Log.e(TAG, "Installation was interrupted.", e);
			finish();
			return;
		}
		catch(IllegalThreadStateException e)
		{
			Log.e(TAG, "Despite waiting for the installation to complete, we attempted to read from it while it was still running.", e);
			finish();
			return;
		}
		
		// Uncomment the line below and Eclipse should give you an
		// "Unreachable code" exception, which will tell you that this
		// Activity will never be created which is exactly what we want.
		finish();
	}
}
