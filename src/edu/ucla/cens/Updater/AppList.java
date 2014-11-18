package edu.ucla.cens.Updater;

import java.util.LinkedList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import edu.ucla.cens.Updater.model.AppInfoModel;
import edu.ucla.cens.Updater.utils.AppInfoCache;
import edu.ucla.cens.Updater.utils.AppManager;
import edu.ucla.cens.Updater.utils.SysappClear;
import edu.ucla.cens.systemlog.Log;

/**
 * The main UI element for the Updater, this class lists all the packages that
 * have updates and that are currently being managed.
 * 
 * @author John Jenkins
 * @version 1.0
 */
public class AppList extends TabActivity implements View.OnClickListener, DialogInterface.OnClickListener, OnItemClickListener
{
	private static final String TAG = "CENS.Updater.AppList";
	
	private static final int RUNNING_INSTALLER = 1;
	
	public static final int MESSAGE_UPDATE_LISTS = 1;
	
	//private Button installButton;
	
	//private ListView listOfUpdateableApps;
	private ListView listOfManagedApps;
	
	private AlertDialog stopManagingDialog;
	
	private PackageDescription[] mManagedPackages;
	private PackageDescription[] mUpdatePackages;
	
	private LinkedList<PackageDescription> newPackages;
	
	private TabHost mTabHost;
	
	private int uninstallIndex;
	private String uninstallString;
	
	// cache of extended info about application. complements the contents
	// of the db
	private AppInfoCache cache = AppInfoCache.get();
	
	
	private static final int COLOR_ORANGE = Color.parseColor("#FF5500");
	
	
	/**
	 * Runs an update in the background.
	 * 
	 * @author John Jenkins
	 */
	private class BackgroundUpdate implements Runnable
	{
		Context mContext;
		
		/**
		 * Needs an application context to create a new Updater object.
		 * 
		 * @param context The Context in which this update is being done.
		 */
		public BackgroundUpdate(Context context)
		{
			mContext = context;
		}
		
		/**
		 * Creates a new Updater object and does an update. If any updates are
		 * found or are pending, it will refresh the updates list. 
		 */
		@Override
		public void run()
		{
			int attempts = 5;
			while (attempts > 0) {
				Log.d(TAG, "Update checkin attempt countdown: " + Integer.toString(attempts));
				Updater updater = new Updater(mContext);
				if (updater.doUpdate()) {
					attempts = 0;
				} else {
					try {
						Log.d(TAG, "Checking failed, sleeping 5 seconds and trying again.");
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					Log.d(TAG, "Done sleeping");
				}
				messageHandler.sendMessage(messageHandler.obtainMessage(MESSAGE_UPDATE_LISTS));
				attempts = attempts - 1;
			}
			//if(updater.doUpdate())
			//{
			//}
		}
	}
	
	private Handler messageHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			if(msg.what == MESSAGE_UPDATE_LISTS)
			{
				updateLists();
			}
		}
	};

	private ColorViewAdapter viewAdapter;
	
	/**
	 * Sets up the UI elements then does an update. An update includes
	 * updating the list of applications.
	 */
	@Override
	public void onCreate(Bundle savedInstance)
	{
		super.onCreate(savedInstance);
		setContentView(R.layout.app_list);
		AppManager.create(getApplicationContext());
        AppManager.get().setActivity(this);
		Log.initialize(this, Database.LOGGER_APP_NAME);
		//cache.load();
		mTabHost = getTabHost();
	    
	    //mTabHost.addTab(mTabHost.newTabSpec("updateableTabSpec").setIndicator("Updates").setContent(R.id.updateable_list_tab));
	    mTabHost.addTab(mTabHost.newTabSpec("managedTabSpec").setIndicator("Managed Apps").setContent(R.id.managed_list_tab));
	    //mTabHost.addTab(mTabHost.newTabSpec("statusTabSpec").setIndicator("Status").setContent(R.id.status_tab));

	    Intent intent = new Intent().setClass(this, StatusActivity.class);

        String tabText = "";
        tabText = "Status";
        TabHost.TabSpec spec = mTabHost.newTabSpec("status").setIndicator(tabText)
                      .setContent(intent);
        mTabHost.addTab(spec);
	    
	    mTabHost.setCurrentTab(0);
		
	    /*
		installButton = (Button) findViewById(R.id.install_updates);
		installButton.setOnClickListener(this);
		
		listOfUpdateableApps = (ListView) findViewById(R.id.updateable_list);
		listOfUpdateableApps.setEmptyView(findViewById(R.id.updateable_list_empty_text));
		listOfUpdateableApps.setOnItemClickListener(this);
		*/
		listOfManagedApps = (ListView) findViewById(R.id.managed_list);
		listOfManagedApps.setEmptyView(findViewById(R.id.managed_list_empty_text));
		listOfManagedApps.setOnItemClickListener(this);
		
		newPackages = new LinkedList<PackageDescription>();
		
	}
	
	/**
	 * Updates the list of classes in case someone else in the application the
	 * lists were updated.
	 */
	@Override
	public void onStart() {
		super.onStart();

		SharedPreferences sharedPreferences = getSharedPreferences(Database.PACKAGE_PREFERENCES, Context.MODE_PRIVATE);
		if(sharedPreferences.getBoolean(Database.PREFERENCES_SELF_UPDATE, true))
		{
			//doUpdate();
			
			sharedPreferences.edit().putBoolean(Database.PREFERENCES_SELF_UPDATE, false).commit();
		}
		else 
		{
			updateLists();
		}
	}
	
	/**
	 * Adds the options to the options menu.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		return true;
	}
	
	/**
	 * Called when an element is clicked. See the internal details for what
	 * happens when a specific element is clicked.
	 */
	@Override
	public void onClick(View v)
	{
		/**
		 * Called when the install button is clicked. Adds any new packages
		 * that have been added by clicking on their unchecked name in the
		 * list of updateable applications to the list of managed packages.
		 * Then, it begins the installer.
		 */
		/*
		if(v == installButton)
		{
			Database db = new Database(this);
			ListIterator<PackageDescription> currPackage = newPackages.listIterator();
			while(currPackage.hasNext())
			{
				PackageDescription next = currPackage.next();
				db.addManaged(next.getQualifiedName(), next.getDisplayName());
				currPackage.remove();
			}
			
			((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(Updater.NOTIFICATION_ID);
			
			Intent intentToFire = new Intent(this, Installer.class);
			startActivityForResult(intentToFire, RUNNING_INSTALLER);
		}
		*/
	}
	
	/**
	 * Called when a dialog button is clicked. See the internal details for
	 * details on what happens when a specific dialog and that dialog's button
	 * is clicked.
	 */
	@Override
	public void onClick(DialogInterface di, int whichButton)
	{
		/**
		 * If the "Yes" button of the dialog that asks users if they want to
		 * stop managing applications is clicked, it stops managing the
		 * package, adds the package to the list of packages that need to be
		 * removed, and updates the lists of updates and managed apps.
		 */
		if(di == stopManagingDialog)
		{
			// Remove the app from being tracked in the database.
			Database db = new Database(this);
			db.stopManaging(mManagedPackages[uninstallIndex].getQualifiedName());
			
			updateLists();
		}
	}
	
	/**
	 * Called when an item in one of the lists is tapped. See the function
	 * details for information on what happens when a specific list is tapped.
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		/**
		 * If the list of package updates was clicked, the following function
		 * is called.
		 */
		//if(parent == listOfUpdateableApps)
		//{
		//	listOfUpdateableAppsClick((CheckedTextView) view, position);
		//}
		/**
		 * If the list of managed packages was clicked, the following function
		 * is called.
		 */
		if(parent == listOfManagedApps)
		{
			listOfManagedAppsClick((TextView) view, position);
		}	
	}
	
	/**
	 * Called when an options menu item is clicked. See the function details
	 * for information on what happens a specific item is clicked.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
		/**
		 * If the "check for updates" item is clicked, do an update.
		 */
		case R.id.menu_update:
			Log.d(TAG, "starting update");
			Toast.makeText(this, "Checking for updates...", Toast.LENGTH_LONG).show();
			doUpdate();
			return true;
		
		/**
		 * Launch the preferences menu.
		 */
		case R.id.menu_preferences:
			Log.d(TAG, "starting CustomPreferenceActivity");
			startActivity(new Intent(this, CustomPreferenceActivity.class));
			return true;


      /**
       *
       */
    case R.id.menu_clear_apps:
      Log.d(TAG, "Starting system app clear");
			final Dialog dialog = new Dialog(this);
			dialog.setContentView(R.layout.clearapp_popup);
			dialog.setTitle("Clear System Apps");
			
			WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
			params.width = WindowManager.LayoutParams.FILL_PARENT;
			dialog.getWindow().setAttributes(params);
			
			final Context applicationContext = getApplicationContext();
      
      			// Setup the register button.
			((Button) dialog.findViewById(R.id.clearappsConfirmButton))
				.setOnClickListener(
						new OnClickListener() {
              /**
							 * Attempt to remove apps
							 */
      			@Override
							public void onClick(View v) {
								try {
									// Get the entered values.
									SysappClear sac = new SysappClear();
									sac.getSystemMount();
									
								}
								catch(Exception e) {
									Log.e(
											TAG, 
											"Exception while clearning apps.", 
											e);
									
									Toast.makeText(
											applicationContext, 
											"ClearApps failed.", 
											Toast.LENGTH_LONG)
										.show();
								}
								
								dialog.dismiss();
							}
						}
				);
			
			// Setup the cancel button.
			((Button) dialog.findViewById(R.id.clearappsCancelButton))
				.setOnClickListener(
						new OnClickListener() {
      		/**
							 * Close the dialog box.
							 */
							@Override
							public void onClick(View v) {
								dialog.dismiss();
							}
				
						}
				);
			
			dialog.show();
			return true;

      
		/**
		 * Register the device with an asset tag.
		 */
    /*
		case R.id.menu_register:
			final Dialog dialog = new Dialog(this);
			dialog.setContentView(R.layout.register_popup);
			dialog.setTitle("Register");
			
			WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
			params.width = WindowManager.LayoutParams.FILL_PARENT;
			dialog.getWindow().setAttributes(params);
			
			final Context applicationContext = getApplicationContext();
			
			// Get the TextViews used to enter data.
			final TextView assetTagTextView = 
				(TextView) dialog.findViewById(R.id.registerId);
			final TextView groupNameTextView = 
				(TextView) dialog.findViewById(R.id.registerGroup);
			
			// Save the desired group name.
			SharedPreferences preferences = 
				getSharedPreferences(
					Database.PACKAGE_PREFERENCES, 
					Context.MODE_PRIVATE);
			groupNameTextView
				.setText(
					preferences
						.getString(
							Updater.PREFERENCE_GROUP_NAME, 
							getString(R.string.default_group)));
			
			// Setup the register button.
			((Button) dialog.findViewById(R.id.registerConfirmButton))
				.setOnClickListener(
						new OnClickListener() {
    */			/**
							 * Attempt to register and then close the dialog.
							 */
      /*			@Override
							public void onClick(View v) {
								try {
									// Get the entered values.
									String assetTag =
										assetTagTextView.getText().toString();
									String groupName =
										groupNameTextView.getText().toString();
									
									// Save the desired group name.
									SharedPreferences preferences = 
										applicationContext
											.getSharedPreferences(
												Database.PACKAGE_PREFERENCES, 
												Context.MODE_PRIVATE);
									preferences
										.edit()
										.putString(
											Updater.PREFERENCE_GROUP_NAME,
											groupName)
										.commit();
									
									// Register the device.
									(new Register(
											applicationContext, 
											assetTag, 
											groupName))
											.doRegister();
								}
								catch(NullPointerException e) {
									Log.e(
											TAG, 
											"Exception while registering.", 
											e);
									
									Toast.makeText(
											applicationContext, 
											"Registration failed.", 
											Toast.LENGTH_LONG)
										.show();
								}
								catch(IllegalArgumentException e) {
									Log.e(
											TAG, 
											"Exception while registering.", 
											e);
									
									Toast.makeText(
											applicationContext, 
											"Registration failed.", 
											Toast.LENGTH_LONG)
										.show();
								}
								
								dialog.dismiss();
							}
						}
				);
			
			// Setup the cancel button.
			((Button) dialog.findViewById(R.id.registerCancelButton))
				.setOnClickListener(
						new OnClickListener() {
      */		/**
							 * Close the dialog box.
							 */
			/*				@Override
							public void onClick(View v) {
								dialog.dismiss();
							}
				
						}
				);
			
			dialog.show();
			return true;
     */
		/**
		 * Otherwise, pass the call to the parent.
		 */
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	/**
	 * Called when an Activity returns. See the function details for 
	 * information on what happens when a specific Activity returns.
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		/**
		 * If the Installer is returning, we update the lists.
		 */
		if(requestCode == RUNNING_INSTALLER)
		{
			updateLists();
		}
	}
	
	/**
	 * Runs an update in the background.
	 */
	private void doUpdate()
	{
		BackgroundUpdate backgroundUpdate = new BackgroundUpdate(this);
		(new Thread(backgroundUpdate)).start();
	}
	
	/**
	 * This is a wrapper function for updating both lists.
	 */
	private void updateLists()
	{
		updateUpdatesList();
		updateManagedList();
	}
	
	/**
	 * Updates the list that contains all updates and apps that aren't
	 * installed but could be installed. It does this by querying the database
	 * for all possible updates and creates PackageDescription objects for
	 * each update. This facilitates the need to display one name to the user
	 * but keep the actual package name associated with the name.
	 * 
	 * If any packages in the list are being managed, then this is an update.
	 * Therefore, they are checked in the UI. If they are not marked as "to be
	 * installed" in the database, they are forcibly marked as such.
	 */
	private void updateUpdatesList()
	{
		// TODO: Add removable apps as well
		Database db = new Database(this);
		
		int numPackages = 0;
		mUpdatePackages = db.getUpdates();
		if(mUpdatePackages == null)
		{
			Log.e(TAG, "Unable to get packages to be installed.");
			mUpdatePackages = new PackageDescription[0];
		}
		else
		{
			numPackages = mUpdatePackages.length;
		}
		
		//listOfUpdateableApps.setAdapter(new ArrayAdapter<PackageDescription>(this, R.layout.checked_list_item, mUpdatePackages));
		
		//boolean buttonEnabled = false;
		for(int i = 0; i < numPackages; i++)
		{
			if(db.isManaged(mUpdatePackages[i].getQualifiedName()))
			{
				//buttonEnabled = true;
				//listOfUpdateableApps.setItemChecked(i, true);
				
				if(!db.getUpdateStatus(mUpdatePackages[i].getQualifiedName()))
				{
					db.changeUpdateStatus(mUpdatePackages[i].getQualifiedName(), true);
				}
			}
		}
		/*
		*/

		//installButton.setEnabled(buttonEnabled);
	}
	
	/**
	 * Updates the list of managed applications. This is taken straight from
	 * the database and put into PackageDescription objects to keep the UI-
	 * friendly display name associated with the internally-referenced package
	 * name.
	 */
	private void updateManagedList()
	{
		Database db = new Database(this);
		
		LinkedList<PackageDescription> managedPackages = db.getMangedDescriptions();
		Object[] oManagedPackages = managedPackages.toArray();
		int numPackages = oManagedPackages.length;
		mManagedPackages = new PackageDescription[oManagedPackages.length];
		for(int i = 0; i < numPackages; i++)
		{
			mManagedPackages[i] = (PackageDescription) oManagedPackages[i];
		}
		viewAdapter = new ColorViewAdapter(this, R.layout.list_item, mManagedPackages);
		listOfManagedApps.setAdapter(viewAdapter);
	}
	

	private class ColorViewAdapter extends ArrayAdapter<PackageDescription> {

		public ColorViewAdapter(Context context, int textViewResourceId,
				PackageDescription[] objects) {
			//super(context, res1, textViewResourceId, objects);
			super(context, textViewResourceId, objects);
		}

		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {  
			TextView view = (TextView) super.getView(position, convertView, parent);  
			PackageDescription desc = getItem(position);
			AppInfoModel appinfo = cache.get(desc.getQualifiedName());
			int color;
			
			if (appinfo == null) {
				color = Color.TRANSPARENT;
			} else {
				if (cache.hasDataRetrievalError()) {
					color = Color.RED;
				} else if (appinfo.installedVersion < appinfo.getVersion()) {
					color = Color.YELLOW;
				} else if (appinfo.installedVersion > appinfo.getVersion()) {
					color = COLOR_ORANGE;
				} else if (appinfo.installedVersion == appinfo.getVersion()) {
					color = Color.GREEN;
				} else {
					color = Color.RED;
				}
			    view.setTextColor(Color.BLACK);
			    view.setText(appinfo.toRichText());
			}
		    view.setBackgroundColor(color);
		    
            //TextView textView=(TextView) view.findViewById(android.R.id.text1);
            //TextView textView=(TextView) view.findViewById(R.layout.list_item);
            //textView.setTextColor(Color.BLUE);
			return view;  
		}		
		
	}
	/**
	 * Called when an item in the list of managed packages was clicked. If the
	 * user was managed, it will inform them that they cannot remove a package
	 * from being managed. If the user is not being managed, it confirms that
	 * the user wants to stop managing the package. The response is then
	 * handled by {@link #onClick(DialogInterface, int)}.
	 * 
	 * @param view The TextView that clicked in the list of managed packages.
	 * 
	 * @param position The position of the 'view' that was clicked in the list
	 * 				   of managed packages. This is used as a reference to the
	 * 				   internal list of managed packages.
	 */
	private void listOfManagedAppsClick(TextView view, int position)
	{
		if (!cache.isEmpty()) {
			AppInfoModel appinfo = cache.getItemAt(position);
			if (appinfo != null && appinfo.lastChecked != null) {
				Toast.makeText(this, appinfo.toRichTextLong(), Toast.LENGTH_LONG).show();			
			}
		}

		// TODO display more details
		
		/*
		// Check that they are not managed.
		SharedPreferences preferences = getSharedPreferences(Database.PACKAGE_PREFERENCES, Context.MODE_PRIVATE);

		// If not manged,
		if(!preferences.getBoolean(Database.PREFERENCES_MANAGED, false))
		{
			// Confirm that they want to stop tracking this app.
			uninstallIndex = position;
			uninstallString = view.getText().toString();
			
			AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
			alertDialog.setMessage("Are you sure you want " + getResources().getString(R.string.app_name) + " to stop managing " + uninstallString + "?");
			alertDialog.setCancelable(false);
			alertDialog.setPositiveButton("Yes", this);
			alertDialog.setNegativeButton("No", null);
			
			stopManagingDialog = alertDialog.create();
			stopManagingDialog.show();
		}
		// If managed,
		else
		{
			Toast.makeText(this, "You are a managed user and this package is already being managed for you. Therefore, you are not allowed to stop managing it.", Toast.LENGTH_SHORT).show();
		}
		*/
	}

	public void notifyDataChanged() {
		viewAdapter.notifyDataSetChanged();
	}
}
