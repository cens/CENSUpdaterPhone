package edu.ucla.cens.Updater.utils;

import java.lang.reflect.InvocationTargetException;

import android.content.Context;
import android.util.Log;

public class SysappClear {

	  private static final String TAG = SysappClear.class.getName();

	  private Exec exec = new Exec();
	  // 1) music  2)email 3) fm radio 4) play store 
	  // 5) sound recorder 6) blacklist number 
	  // 8)skype  9)messenger 10) movies 
	  private static final String[] app_clear_list =
		  {	"MagicSmokeWallpapers.apk", "Google_Play_Store_v3_9_16.apk", "MessengerWithYou.apk",
		  	"FMPlayer.apk", "Email.apk", "Calendar.apk",
		  	"Music.apk", "SoundRecorder.apk",
		  	"VisualizationWallpapers.apk", "SprdNote.apk",
        "BaiduSearch_Android_1-0-9-95_7300195a.apk",
        "FMRadio.apk", "Gallery3D.apk", "Gmail.apk", "MarketUpdater.apk",
        "CalendarProvider.apk", "GoogleCalendarSyncAdapter.apk",
        "Street.apk", "YouTube.apk", "NotePad.apk", "FMRadio.apk",
        "GooglePartnerSetup.apk", "GoogleQuickSearchBox.apk",
        "Talk.apk", "VoiceSearch.apk","GoogleContactsSyncAdapter.apk",
        "HwWallpaperChooser.apk", "BeyondTheSkyTheme.apk",
		  	"com.skype.rover.apk", "CallFireWall.apk"
		  	};
  //private static final String[] framework_clear_list =
  //  { "com.google.android.maps.jar", "com.google.android.maps.odex"};
  
	  /*
	  	"", "", "",
	  	"", "", "",
	  	"", "", "",
	  	"", "", "",
	  */

	  public String getSystemMount() {
      String cmd = "mount";
      String msg = "";
      try {
        msg = exec.execAsSu_readyversion(cmd, "system");
        Log.i(TAG, "Success: " + cmd + ": " + msg);
        String[] mountres = msg.split("\n");
        String partition = "";
        boolean foundsystem = false;
        for (String r: mountres) {
        	String[] splitrow = r.split(" ");
        	if (splitrow.length >= 2) {
        		if (splitrow[1].contains("/system")) {
        			partition = splitrow[0];
        			break;
        		}
        		
        	}
        }	
        
        Log.i(TAG, "FOUND SYSTEM PARTITION " + partition);
        
        cmd = "mount -o rw,remount " + partition + " /system";
        msg = exec.execAsSu_nooutput_timeout(cmd, 3);
        Log.i(TAG, msg);

        // CLEAR SYSTEM APPS
        cmd = "";
        for (String clear_app: app_clear_list) {
          cmd += "rm /system/app/" + clear_app + "; ";
        }
        
        try {
          msg = exec.execAsSu_nooutput_timeout(cmd, 10);
          Log.i(TAG, msg);
        } catch (Exception e) {
          Log.i(TAG, "FAILED: " + msg + ": " + e);
        }

        // CLEAR FRAMEWORKAPPS
        /*
        cmd = "";
        for (String clear_app: framework_clear_list) {
          cmd += "rm /system/framework/ " + clear_app + "; ";
        }
        
        try {
          msg = exec.execAsSu_nooutput_timeout(cmd, 5);
          Log.i(TAG, msg);
        } catch (Exception e) {
          Log.i(TAG, "FAILED: " + msg + ": " + e);
        }
        */
        
        // SYNC
        cmd = "sync";
        msg = exec.execAsSu_nooutput_timeout(cmd, 5);
        Log.i(TAG, msg);

        // REMOUNT
        cmd = "mount -o ro,remount " + partition + " /system";
        msg = exec.execAsSu_nooutput_timeout(cmd, 5);
        Log.i(TAG, msg);

        // REBOOT
        cmd = "/system/bin/reboot";
        msg = exec.execAsSu_nooutput_timeout(cmd, 5);
        Log.i(TAG, msg);
        
        
      } catch (RuntimeException e) {
        Log.e(TAG, "Failed to exec " + cmd + ": " + e);
        msg = e.toString();
      }
      return msg;
	  }

}
