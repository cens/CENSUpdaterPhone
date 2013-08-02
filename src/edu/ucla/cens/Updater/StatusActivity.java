package edu.ucla.cens.Updater;


import edu.ucla.cens.Updater.model.OnChangeListener;
import edu.ucla.cens.Updater.model.StatusModel;
import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

public class StatusActivity extends Activity  implements OnChangeListener<StatusModel>{

	private TextView statusText;
	private StatusModel model = StatusModel.get();
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.status_activity);
		
		statusText = (TextView) findViewById(R.id.status_text);
		StatusModel.get().addListener(this);
		refreshViews();
	}
	
	@Override
	public void onChange(StatusModel model) {
		runOnUiThread( new Runnable() {
			
			@Override
			public void run() {
				refreshViews();
			}
		});
	}
	
	/**
	 * Refresh the view.
	 */
	private void refreshViews() {
		model.dump();
		String source = renderStatusHtml();
		statusText.setText(Html.fromHtml(source));
	}

	private static final String template1 = "<em>Last error:</em> %s: %s<br/>";
	private static final String template2 = "<em>Last successful install:</em> %s: %s<br/>";
	private static final String template3 = "<em>Last download:</em> %s: %s<br/>";
	
	/**
	 * Renders status in rich format in HTML.
	 * @return the html
	 */
	private String renderStatusHtml() {
		String ret = "";
		if (model.getLastErrorTs()!= null) {
			ret += String.format(template1, 
					model.getLastErrorTs(), model.getLastErrorMessage());
		}
		if (model.getLastCheckTs()!= null) {
			ret += String.format(template2,
					model.getLastCheckTs(), model.getLastCheckMessage());
		}
		if (model.getLastDownloadTs()!= null) {
			ret += String.format(template3,
					model.getLastDownloadTs(), model.getLastDownloadMessage());
		}
		if (ret.length() == 0) {
			ret = "<br/><em>There are no updates to report yet</em><br>";
		}
		return ret;
	}

}