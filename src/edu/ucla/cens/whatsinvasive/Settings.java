package edu.ucla.cens.whatsinvasive;

import java.io.File;
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import edu.ucla.cens.whatsinvasive.services.LocationService;
import edu.ucla.cens.whatsinvasive.services.LocationService.TagUpdateThread;
import edu.ucla.cens.whatsinvasive.services.UploadService;
import edu.ucla.cens.whatsinvasive.tools.UpdateThread.UpdateData;

public class Settings extends PreferenceActivity implements Observer {
	private final int ACTIVITY_AREALIST = 0;
	private final int ACTIVITY_LOGIN = 2;
	public static final String PREFERENCES_USER = "user";
	private static final int MESSAGE_COMPLETE_TAG = 0;
	private static final int MESSAGE_TIMEOUT_TAG = 1;
	private static final int MESSAGE_NO_RESPONSE_TAG = 2;
	private static final int SETTINGS_HELP = 0;
	protected static final int HELP_IMAGE = 123;
	private SharedPreferences mPreferences;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.title_settings);
		
		// Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
        
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
	}

	@Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
	    if(preference.getKey().equals("upload_service_on")) {
	        if(((CheckBoxPreference)preference).isChecked()) {
	            startService(new Intent(this, UploadService.class));
	        } else {
	            stopService(new Intent(this, UploadService.class));
	        }
	        return true;
	    } else if(preference.getKey().equals("location_service_on")) {
	        if(((CheckBoxPreference)preference).isChecked()) {
                startService(new Intent(this, LocationService.class));
            } else {
                stopService(new Intent(this, LocationService.class));
            }
	        return true;
	    } else if(preference.getKey().equals("select_location")) {
	        Intent intent = new Intent(this, AreaList.class);
            startActivityForResult(intent, ACTIVITY_AREALIST);
	        return true;
	    } else if(preference.getKey().equals("refresh_lists")) {
	        showToast(getString(R.string.attempting_download_new_invasive), Toast.LENGTH_SHORT);
            TagUpdateThread thread = new TagUpdateThread(this, LocationService.getParkId(this));
            thread.getObservable().addObserver(this);
            thread.start();
            //setProgressBarIndeterminateVisibility(true);
	        return true;
	    } else if(preference.getKey().equals("reset_login")) {
	        Intent intent = new Intent(this, Login.class);
            startActivityForResult(intent, ACTIVITY_LOGIN);
	        return true;
	    } else if(preference.getKey().equals("send_debug")) {
	        File file = new File(Environment.getExternalStorageDirectory(), "whatsinvasive.zip");
	        
	        if(!file.exists()) {
	            showToast(getString(R.string.send_debug_not_collected), Toast.LENGTH_SHORT);
	        } else {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_EMAIL, getResources().getStringArray(R.array.debug_emails));
                intent .putExtra(Intent.EXTRA_SUBJECT, 
                        "Debugging Info for " + mPreferences.getString("username", ""));
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                intent.setType("application/zip");
                startActivity(Intent.createChooser(intent, getString(R.string.chooser_send_debug)));
	        }
	        return true;
	    }
        
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

	protected void showToast(String text,int length){
		Toast.makeText(this, text, length).show();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode){
			case ACTIVITY_AREALIST:
				if(resultCode == AreaList.RESULT_TAGS_UPDATED) {
					Toast.makeText(this, getString(R.string.tag_list_updated), Toast.LENGTH_SHORT).show();
				} else if(resultCode == AreaList.RESULT_TAGS_SAME) {
					Toast.makeText(this, getString(R.string.tag_list_use_existing), Toast.LENGTH_SHORT).show();
				}

				refreshView();
				
				break;
			case ACTIVITY_LOGIN:
				if(resultCode == Activity.RESULT_OK) {
					mPreferences.edit().putBoolean("first_run", false).commit();
					
					DialogInterface.OnClickListener listener2 = new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface dialog, int i) {
							Intent intent = new Intent(Settings.this, HelpImg.class);
							switch (i) {
							case AlertDialog.BUTTON1:
								mPreferences.edit().putBoolean("upload_service_on", true).commit();
				                refreshView();
								startActivityForResult(intent, HELP_IMAGE);
							break;
							case AlertDialog.BUTTON2:
								mPreferences.edit().putBoolean("upload_service_on", false).commit();	
				                refreshView();
								startActivityForResult(intent, HELP_IMAGE);
							break;
							}
						}
					};
					
					AlertDialog dialog = new AlertDialog.Builder(this).create();
					dialog.setMessage(getString(R.string.notice_auto_upload));
					dialog.setButton(getString(R.string.sure), listener2);
					dialog.setButton2(getString(R.string.no_thanks), listener2);
					dialog.show();
				}
				
				break;
			case HELP_IMAGE:
				if (resultCode == RESULT_OK) {
					TagLocation.isLocationEnabled(Settings.this);
				}
				break;
			case WhatsInvasive.CHANGE_GPS_SETTINGS:
				Settings.this.finish();
				break;
		}
		
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	// Hack method to refresh settings view when we programatically change the settings.
	private void refreshView() {
	    getPreferenceScreen().removeAll();
	    addPreferencesFromResource(R.xml.preferences);
    }

    private final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what){
			case MESSAGE_COMPLETE_TAG:
			    setProgressBarIndeterminateVisibility(false);
				showToast(getString(R.string.tag_list_download_complete), Toast.LENGTH_SHORT);
				break;
			case MESSAGE_TIMEOUT_TAG:
				showToast(getString(R.string.tag_list_download_failed_http), Toast.LENGTH_LONG);
				break;
			case MESSAGE_NO_RESPONSE_TAG:
				showToast(getString(R.string.tag_list_download_failed_server), Toast.LENGTH_LONG);
				break;
			}
		}
	};

	public void update(Observable observable, Object data) {	
		UpdateData update = (UpdateData) data;
		if(update.source.equals("TagUpdateThread")){
			if(update.allDone){
				this.handler.sendEmptyMessage(MESSAGE_COMPLETE_TAG);
			}else if(update.description.equals("timeout")){
				this.handler.sendEmptyMessage(MESSAGE_TIMEOUT_TAG);
			}else if(update.description.equals("no_response")){
				this.handler.sendEmptyMessage(MESSAGE_NO_RESPONSE_TAG);
			}
			
		}
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, SETTINGS_HELP, 0, getString(R.string.help_settings_title)).setIcon(android.R.drawable.ic_menu_help);	
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = null;
		switch(item.getItemId()) {
		case SETTINGS_HELP:
			intent = new Intent(this, HelpImg.class); 
			intent.putExtra("help type", HelpImg.SETTINGS_HELP);
			startActivity(intent);
			break;
		}

		return super.onOptionsItemSelected(item);
	}
	
}
