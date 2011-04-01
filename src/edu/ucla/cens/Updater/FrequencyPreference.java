package edu.ucla.cens.Updater;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.preference.Preference;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import edu.ucla.cens.systemlog.Log;

/**
 * A specific View inside the preferences Activity that allows a user to
 * specify an automatic update interval.
 * 
 * Currently, it allows 1 - 99 minutes or hours. The minimum amount of time is
 * every minute, and the maximum amount of time is 99 hours. Neither of which
 * is recommended but are possible.
 * 
 * @author John Jenkins
 * @version 1.0
 */
public class FrequencyPreference extends Preference implements TextWatcher, OnItemSelectedListener
{
	private static final String TAG = "CENS.Updater.FrequencyPreference";
	
	/**
	 * A String used to retrieve the value from the preferences.
	 */
	private static final String VALUE_STRING = "value";
	/**
	 * The default value for the frequency at which updates will be checked.
	 */
	private static final int DEFAULT_UPDATE_VALUE = 2;
	
	/**
	 * A String used to retrieve the modifier from the preferences.
	 */
	private static final String MODIFIER_STRING = "modifier";
	/**
	 * The default modifier for the frequency at which updates will be
	 * checked.
	 */
	private static final int DEFAULT_UPDATE_MODIFIER = 1;
	
	private EditText value;
	private Spinner modifier;
	
	/**
	 * Creates a new FrequencyPreference object within this Context.
	 * 
	 * @param context The Context in which to create this new object.
	 */
	public FrequencyPreference(Context context)
	{
		super(context);
		
		Log.initialize(context, Database.LOGGER_APP_NAME);
	}
	
	/**
	 * Creates a new FrequencyPreference object within this Context with the
	 * parameterized AttributeSet.
	 * 
	 * @param context The Context in which to create this object.
	 * 
	 * @param attrs The AttributeSet to apply to this object from an inflated
	 * 				XML.
	 */
	public FrequencyPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		
		Log.initialize(context, Database.LOGGER_APP_NAME);
	}
	
	/**
	 * Creates a new FrequencyPreference object within this Context with the
	 * parameterized AttributeSet and a class-specific base style.
	 * 
	 * @param context The Context in which to create this object.
	 * 
	 * @param attrs The AttributeSet to apply to this object from an inflated
	 * 				XML.
	 * 
	 * @param defStyle The class-specific base style to apply to this object.
	 */
	public FrequencyPreference(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		
		Log.initialize(context, Database.LOGGER_APP_NAME);
	}

	/**
	 * Creates the View which contains the header information retrieved from
	 * the XML, an EditText box that will contain the value of the time
	 * period, and a modifier for the time period (minutes, hours, etc.). 
	 */
	@Override
	protected View onCreateView(ViewGroup parent)
	{
		LinearLayout layout = new LinearLayout(getContext());
		layout.setOrientation(LinearLayout.HORIZONTAL);
		layout.setPadding(6, 18, 6, 18);
		
		TextView title = new TextView(getContext());
		title.setText(getTitle());
		title.setTextSize(18);
		title.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
		
		LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		titleParams.gravity = Gravity.LEFT;
		titleParams.weight = 1.0f;
		title.setLayoutParams(titleParams);
		
		value = new EditText(getContext());
		value.setTextSize(16);
		value.addTextChangedListener(this);
		// Allow only unsigned, non-decimal digits.
		value.setKeyListener(new DigitsKeyListener(false, false));
		// Allow only a maximum of 2 digits.
		InputFilter[] filtersArray = new InputFilter[1];
		filtersArray[0] = new InputFilter.LengthFilter(2);
		value.setFilters(filtersArray);
		
		LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		valueParams.gravity = Gravity.RIGHT;
		valueParams.weight = 0.0f;
		value.setLayoutParams(valueParams);

		modifier = new Spinner(getContext());
		modifier.setOnItemSelectedListener(this);
		
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(), R.array.time_ranges, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		modifier.setAdapter(adapter);
		modifier.setSelection(0);
		
		LinearLayout.LayoutParams modifierParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		modifierParams.gravity = Gravity.RIGHT;
		modifierParams.weight = 0.0f;
		modifier.setLayoutParams(modifierParams);
		
		layout.addView(title);
		layout.addView(value);
		layout.addView(modifier);
		layout.setId(android.R.id.widget_frame);
		
		SharedPreferences preferences = getSharedPreferences();
		value.setText(Integer.toString(preferences.getInt(VALUE_STRING, DEFAULT_UPDATE_VALUE)));
		modifier.setSelection(preferences.getInt(MODIFIER_STRING, DEFAULT_UPDATE_MODIFIER));
		
		return layout;
	}

	/**
	 * Checks to ensure that the value is non-zero and a valid number. If so,
	 * it updates the preferences with the new "value" value.
	 */
	@Override
	public void afterTextChanged(Editable s)
	{
		try
		{
			int newValue = Integer.decode(value.getText().toString());
			if(newValue <= 0)
			{
				Toast.makeText(getContext(), "The value must be greater than zero.", Toast.LENGTH_SHORT).show();
				return;
			}
			else if(getSharedPreferences().getInt(VALUE_STRING, -1) != newValue)
			{
				Log.i(TAG, "Setting update value to " + newValue);
				getSharedPreferences().edit().putInt(VALUE_STRING, newValue).commit();
				notifyChanged();
			}
		}
		catch(NumberFormatException e)
		{
			Toast.makeText(getContext(), "Invalid duration.", Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Unused.
	 */
	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{
		// Do nothing.
	}

	/**
	 * Unused.
	 */
	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
		// Do nothing.	
	}
	
	/**
	 * Updates the preferences with the new "modifier" value based on the
	 * currently selected index. This requires that the Spinner that controls
	 * the modifier not reorder the values from when they were inserted.
	 */
	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
	{
		// For whatever reason, this function is being continuously called, so
		// I am putting this here to alleviate the preferences file.
		if(getSharedPreferences().getInt(MODIFIER_STRING, -1) != position)
		{
			Log.i(TAG, "Setting update modifier to " + ((position == 0) ? "minutes" : "hours") + ".");
			getSharedPreferences().edit().putInt(MODIFIER_STRING, position).commit();
			notifyChanged();
		}
	}

	/**
	 * Unused.
	 */
	@Override
	public void onNothingSelected(AdapterView<?> parent)
	{
		// This should never happen as something should always be selected.
	}
	
	/**
	 * Looks up the current values of the preferences and calculates the
	 * number of milliseconds between updates.
	 * 
	 * @return Number of milliseconds between consecutive, automatic update
	 * 		   checks.
	 */
	public long getUpdateFrequencyInMillis()
	{
		SharedPreferences preferences = getSharedPreferences();
		
		// Default to 1 minute. This will only ever happen when the phone is
		// booting and we get a null value for preferences. When it checks for
		// the second time, the phone will have booted and preferences won't
		// be null. At this point we will get the true update period.
		long result;
		if(preferences != null)
		{
			int value = preferences.getInt(VALUE_STRING, DEFAULT_UPDATE_VALUE);
			int modifier = preferences.getInt(MODIFIER_STRING, DEFAULT_UPDATE_MODIFIER);
			
			result = value * ((modifier == 0) ? 60000l : 3600000l);
		}
		else
		{
			result = DEFAULT_UPDATE_VALUE * ((DEFAULT_UPDATE_MODIFIER == 0) ? 60000l : 3600000l);
			
			Log.i(TAG, "Preferences is 'null'. Returning default time of: " + result);
		}
		
		return result;
	}
}
