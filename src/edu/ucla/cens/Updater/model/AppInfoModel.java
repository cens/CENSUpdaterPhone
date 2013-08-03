package edu.ucla.cens.Updater.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.text.Html;
import android.text.Spanned;

import edu.ucla.cens.Updater.PackageInformation;
import edu.ucla.cens.Updater.utils.AppInfoCache;


/**
 * Model for a managed application/.apk 
 *
 */
public class AppInfoModel extends PackageInformation {

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("M/d HH:mm", Locale.US);
	private static final SimpleDateFormat dateFormatLonger = new SimpleDateFormat("M/d/yy HH:mm", Locale.US);
	private static final SimpleDateFormat dateFormatLongerPrecise = new SimpleDateFormat("M/d/yy HH:mm:ss", Locale.US);
	
	public String apkPathname;
	public Date lastChecked;
	/**
	 * Status code of last check.
	 * 0 if success, error code otherwise
	 */
	public int lastCheckedStatus;
	public String lastCheckedMessage = "";
	
	public int installedVersion;
	public Date lastInstallTime;
	
	//public String installedVersionName;

	public AppInfoModel(String qualifiedName, String releaseName,
			String displayName, int version, String url, Action action)
			throws IllegalArgumentException {
		super(qualifiedName, releaseName, displayName, version, url, action);
	}
	
	
	/**
	 * Returns the user-friendly name of this package as this is mostly used
	 * by internal functions so this class can be used by lists and arrays.
	 */
	@Override
	public String toString()
	{
		String ret = super.toString();
		ret += "\t" + lastCheckedMessage;
		return ret;
	}
	
	
	String template = 
		"<em>%s</em>\t%s:  %s\t<small>%s -> %s<small>";
	String template2 = "<br/><em>Last install:</em> %s";
	
	public Spanned toRichText() {
		//String source;
		String lastCheckedStr = null;
		if (lastChecked != null) {
			lastCheckedStr = dateFormat.format(lastChecked);
		}
		String source = String.format(template, getDisplayName(), lastCheckedStr, lastCheckedMessage,
				installedVersion, getVersion());
		if (lastInstallTime != null) {
			source += String.format(template2, dateFormatLonger.format(lastInstallTime));
		}
		return Html.fromHtml(source);
	}

	
	String ltemplate = "<em>%s</em> %s<br/>Last checked: %s<br/>Check result: %s<br/>Installed version: %s<br/>Available version: %s";

	public Spanned toRichTextLong() {
		String source;
		AppInfoCache cache = AppInfoCache.get();
		if (cache.hasDataRetrievalError()) {
			lastCheckedMessage = cache.getDataRetrievalError();
		}
		source = String.format(ltemplate, getDisplayName(), getQualifiedName() , dateFormatLongerPrecise.format(lastChecked), lastCheckedMessage,
				installedVersion, getVersion());
		if (lastInstallTime != null) {
			source += String.format(template2, dateFormatLonger.format(lastInstallTime));
		}
		return Html.fromHtml(source);
	}
}

