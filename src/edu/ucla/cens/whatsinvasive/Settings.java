package edu.ucla.cens.whatsinvasive;

import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;
import android.widget.ToggleButton;
import edu.ucla.cens.whatsinvasive.services.LocationService;
import edu.ucla.cens.whatsinvasive.services.LocationService.TagUpdateThread;
import edu.ucla.cens.whatsinvasive.services.UploadService;
import edu.ucla.cens.whatsinvasive.tools.UpdateThread.UpdateData;

public class Settings extends Activity implements Observer {
	private final int ACTIVITY_AREALIST = 0;
	private final int ACTIVITY_LOGIN = 2;
	public static final String PREFERENCES_USER = "user";
	private static final int MESSAGE_COMPLETE_TAG = 0;
	private static final int MESSAGE_TIMEOUT_TAG = 1;
	private static final int MESSAGE_NO_RESPONSE_TAG = 2;
	private static final int SETTINGS_HELP = 0;
	protected static final int HELP_IMAGE = 123;
	private Toast setToast;
	private SharedPreferences m_preferences;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.settings);
		setTitle(R.string.title_settings);
		
		m_preferences = this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
		
		setupEvents();
	}

	private void setupEvents() {
		ToggleButton uploadToggle = (ToggleButton) this.findViewById(R.id.ToggleButtonUpload);
		
		if(m_preferences.getBoolean("uploadServiceOn", true)) {
			uploadToggle.setChecked(true);
		}
		
		uploadToggle.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				m_preferences.edit().putBoolean("uploadServiceOn", isChecked).commit(); 

				if(!isChecked)
					Settings.this.stopService(new Intent(Settings.this, UploadService.class));
				else
					Settings.this.startService(new Intent(Settings.this, UploadService.class));
			}});
		
		Button refreshButton = (Button)findViewById(R.id.TagRefreshButton);
		refreshButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {	
				setToast = Toast.makeText(Settings.this, getString(R.string.attempting_download_new_invasive), Toast.LENGTH_SHORT);
				setToast.show();
				TagUpdateThread thread = new TagUpdateThread(Settings.this, LocationService.getParkId(Settings.this));
				thread.getObservable().addObserver(Settings.this);
				thread.start();
				setProgressBarIndeterminateVisibility(true);
			}});	
		
		ToggleButton locationToggle = (ToggleButton)findViewById(R.id.ToggleButtonLocation);
		
		if(m_preferences.getBoolean("locationServiceOn", true)) {
			locationToggle.setChecked(true);
		}
		
		locationToggle.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if(isChecked) {
					m_preferences.edit().putBoolean("locationServiceOn", true).commit();
					
					if(!TagLocation.isLocationEnabled(Settings.this)){
						m_preferences.edit().putBoolean("locationServiceOn", false).commit();
						
						buttonView.setChecked(false);
					}
				} else {
					m_preferences.edit().putBoolean("locationServiceOn", false).commit();
				}
				
				Intent service = new Intent(Settings.this, LocationService.class);
				Settings.this.stopService(service);
				Settings.this.startService(service);
			}});
		
		Button button = (Button)findViewById(R.id.ButtonLogin);
		button.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {						
//				m_preferences.edit().remove("username").commit();
//				m_preferences.edit().remove("password").commit();	
//				m_preferences.edit().remove("Seen Tag Help").commit();
//				m_preferences.edit().remove("firstRun").commit();
				
				Intent intent = new Intent(Settings.this, Login.class);
				Settings.this.startActivityForResult(intent, ACTIVITY_LOGIN);
			}}
		);
		 
		button = (Button)findViewById(R.id.ButtonParks);
		button.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				Intent intent = new Intent(Settings.this, AreaList.class);
    				Settings.this.startActivityForResult(intent, ACTIVITY_AREALIST);
    			}}
		);
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
				
				ToggleButton locationToggle = (ToggleButton)findViewById(R.id.ToggleButtonLocation);		
				
				if(m_preferences.getBoolean("locationServiceOn", true)) {
		            locationToggle.setChecked(true);
		        } else {
		            locationToggle.setChecked(false);
		        }
				break;
			case ACTIVITY_LOGIN:
				if(resultCode == Activity.RESULT_OK) {
					m_preferences.edit().putBoolean("firstRun", false).commit();
					
					DialogInterface.OnClickListener listener2 = new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface dialog, int i) {
							Intent intent = new Intent(Settings.this, HelpImg.class);
							switch (i) {
							case AlertDialog.BUTTON1:
								m_preferences.edit().putBoolean("uploadServiceOn", true).commit();							
								Settings.this.startActivityForResult(intent, HELP_IMAGE);
							break;
							case AlertDialog.BUTTON2:
								m_preferences.edit().putBoolean("uploadServiceOn", false).commit();						
								Settings.this.startActivityForResult(intent, HELP_IMAGE);
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
