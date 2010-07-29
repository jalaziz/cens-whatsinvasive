package edu.ucla.cens.whatsinvasive;
	
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.OnItemClickListener;
import edu.ucla.cens.whatsinvasive.data.TagDatabase;
import edu.ucla.cens.whatsinvasive.data.TagDatabase.AreaRow;
import edu.ucla.cens.whatsinvasive.services.LocationService;
import edu.ucla.cens.whatsinvasive.services.LocationService.AreaUpdateThread;
import edu.ucla.cens.whatsinvasive.services.LocationService.TagUpdateThread;
import edu.ucla.cens.whatsinvasive.tools.UpdateThread.UpdateData;

public class AreaList extends Activity implements Observer {

	private final int DIALOG_DOWNLOAD_AREAS = 0;
	private final int DIALOG_DOWNLOAD_TAGS = 1;
	private final int DIALOG_TIMEOUT_AREA = 2;
	private final int DIALOG_TIMEOUT_TAG = 3;
	private final int DIALOG_NO_RESPONSE_TAG = 4;
	
	private final int MESSAGE_COMPLETE_AREA = 0;
	private final int MESSAGE_COMPLETE_TAG = 1;
	private final int MESSAGE_DOWNLOAD_TAGS = 2;
	private final int MESSAGE_TIMEOUT_AREA = 3;
	private final int MESSAGE_TIMEOUT_TAG = 4;
	private final int MESSAGE_RETRY_AREA = 5;
	private final int MESSAGE_CANCEL = 7;
	private final int MESSAGE_NO_RESPONSE_TAG = 8;

	public static final int RESULT_TAGS_UPDATED = 0;
	
	private static final String TAG = "AreaList";

	protected static final int RESULT_TAGS_SAME = 22;
	
	private final int VISIBLE_PARKS = 10;
	
	private boolean locationServiceOn = true;
	
	private SharedPreferences m_preferences;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		this.setContentView(R.layout.area_list);
		
		m_preferences = this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
		locationServiceOn = m_preferences.getBoolean("locationServiceOn", true);
		
		m_preferences.edit().putBoolean("locationServiceOn", false).commit();
	}
	
	@Override 
	protected void onResume()
	{
		super.onResume();
		
		// Download complete list of areas
		this.showDialog(DIALOG_DOWNLOAD_AREAS);
		
		AreaUpdateThread thread = new AreaUpdateThread(this, false, LocationService.getLocation(this));
		thread.getObservable().addObserver(this);
		
		thread.start();	
	}
	
	private void setupList()
	{
		LocationManager manager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		
		final TagDatabase db = new TagDatabase(this);
		db.openRead();
		
		Cursor areas = null;
		boolean orderedByName = false;
		
		if(!(manager.isProviderEnabled("gps") || manager.isProviderEnabled("network"))){
			// TODO: Location is disable, show dialog
			areas = db.getAreasByName();
			
			orderedByName = true;
		}else{
			// Check if we can actually get a location
			Location location = null;
			
			if(manager.isProviderEnabled("gps"))
				location = manager.getLastKnownLocation("gps");
			else if(manager.isProviderEnabled("network"))
				location = manager.getLastKnownLocation("network");
			
			if(location==null){
				// Didn't get anything so ask user to set a location for now
				areas = db.getAreasByName();
				
				orderedByName = true;
			}else{
				// This assumes that the list is already sorted by the location service
				areas = db.getAreas(VISIBLE_PARKS);
			}
		}
		
		this.startManagingCursor(areas);
		
		try {
			SimpleCursorAdapter adapter = null;
			
			if(!orderedByName)
				adapter = new SimpleCursorAdapter(this, R.layout.area_list_item, areas, new String[]{TagDatabase.KEY_TITLE, TagDatabase.KEY_DISTANCE}, new int[]{android.R.id.text1, android.R.id.text2});
			else
				adapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_1, areas, new String[]{TagDatabase.KEY_TITLE}, new int[]{android.R.id.text1});
			
			ListView list = (ListView) this.findViewById(R.id.ListView01);
			list.setAdapter(adapter);
			list.setOnItemClickListener(new OnItemClickListener(){
	
				public void onItemClick(AdapterView<?> arg0, View arg1, int position,
						long id) {
					// Set fixed park and location
					db.openRead();
					AreaRow row = db.getArea(id);
					db.close();
					
					Bundle data = new Bundle();
					data.putLong("id", row.id);
					data.putDouble("latitude", row.latitude);
					data.putDouble("longitude", row.longitude);
					
					Message msg = new Message();
					msg.what = MESSAGE_DOWNLOAD_TAGS;
					msg.setData(data);
					
					AreaList.this.handler.sendMessage(msg);
					//editor.putBoolean("locationServiceOn", false).commit();
				}});
		} catch(RuntimeException ex) {
			ex.printStackTrace();
		}
		
		db.close();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        		// User effectively canceled manual selection show return to original mode
	        	SharedPreferences preferences = AreaList.this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
	
	        	preferences.edit().putBoolean("locationServiceOn", locationServiceOn).commit();
	        	
	        	this.dismissDialog(DIALOG_DOWNLOAD_AREAS);
	        	
	        	this.setResult(RESULT_OK);
	        	
	        	this.finish();
        	
	        	return true;
        }
        
        return false;
    }

	@Override
	protected Dialog onCreateDialog(int id)
	{
		Dialog dialog = null;
		ProgressDialog progress;
		
		switch(id){
			case DIALOG_DOWNLOAD_AREAS:
				progress = new ProgressDialog(this);
				
				progress.setTitle(getString(R.string.area_list_updating));
				progress.setMessage(getString(R.string.area_list_wait));
				progress.setIndeterminate(true);
				progress.setCancelable(false);
				
				dialog = progress;
				
				break;
			case DIALOG_DOWNLOAD_TAGS:
				progress = new ProgressDialog(this);
				
				progress.setTitle(getString(R.string.area_list_updating_invasives));
				progress.setMessage(getString(R.string.area_list_wait_images));
				progress.setIndeterminate(true);
				progress.setCancelable(false);
				
				dialog = progress;
				
				break;
			case DIALOG_TIMEOUT_AREA:
				dialog = new AlertDialog.Builder(this)
				.setTitle(getString(R.string.area_list_timeout))
				.setMessage(getString(R.string.area_list_fail))
				.setPositiveButton(getString(R.string.area_list_retry_button), new DialogInterface.OnClickListener(){

					public void onClick(DialogInterface dialog, int which) {
						AreaList.this.handler.sendEmptyMessage(MESSAGE_RETRY_AREA);
					}})
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener(){

					public void onClick(DialogInterface dialog, int which) {
						AreaList.this.handler.sendEmptyMessage(MESSAGE_CANCEL);
					}})
				
				.create();
			case DIALOG_TIMEOUT_TAG:
				dialog = new AlertDialog.Builder(this)
				.setTitle(getString(R.string.area_list_timeout))
				.setMessage(getString(R.string.area_list_fail))
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){

					public void onClick(DialogInterface dialog, int which) {
					}})
				
				.create();
				
				break;
			case DIALOG_NO_RESPONSE_TAG:
				dialog = new AlertDialog.Builder(this)
				.setTitle(getString(R.string.area_list_timeout))
				.setMessage(getString(R.string.area_list_fail))
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){

					public void onClick(DialogInterface dialog, int which) {
					}})
				
				.create();
				
				break;
		}
		
		return dialog;
	}
	
	private final Handler handler = new Handler()
	{
		@Override
		public void handleMessage(Message msg){
			switch(msg.what){
				case MESSAGE_DOWNLOAD_TAGS:
					SharedPreferences preferences = AreaList.this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
					Editor editor = preferences.edit();
					
					editor.putString("fixedLocation",  msg.getData().getDouble("latitude") +","+ msg.getData().getDouble("longitude")).commit();
					editor.putLong("fixedArea", msg.getData().getLong("id")).commit();
					TagDatabase db = new TagDatabase(AreaList.this);
					db.openRead();

					int availableTags = db.getTagsAvailable(msg.getData().getLong("id"), null);

					db.close();
					if(availableTags == 0){
						TagUpdateThread thread = new TagUpdateThread(AreaList.this, msg.getData().getLong("id"));
						thread.getObservable().addObserver(AreaList.this);
						
						thread.start();
						AreaList.this.showDialog(DIALOG_DOWNLOAD_TAGS);
					}
					else{
						AreaList.this.setResult(RESULT_TAGS_SAME);
						AreaList.this.finish();
					}
					break;
					
				case MESSAGE_COMPLETE_AREA:
					dismissDialog(DIALOG_DOWNLOAD_AREAS);
					
					AreaList.this.setupList();
					
					break;
				case MESSAGE_COMPLETE_TAG:
					dismissDialog(DIALOG_DOWNLOAD_TAGS);
					
					AreaList.this.setResult(RESULT_TAGS_UPDATED);
					AreaList.this.finish();
					
					break;
				case MESSAGE_TIMEOUT_AREA:
					AreaList.this.dismissDialog(DIALOG_DOWNLOAD_AREAS);
					AreaList.this.showDialog(DIALOG_TIMEOUT_AREA);
					break;
				case MESSAGE_TIMEOUT_TAG:
					AreaList.this.dismissDialog(DIALOG_DOWNLOAD_TAGS);
					AreaList.this.showDialog(DIALOG_TIMEOUT_TAG);
					break;
				case MESSAGE_RETRY_AREA:
					AreaList.this.showDialog(DIALOG_DOWNLOAD_AREAS);
					
					AreaUpdateThread thread2 = new AreaUpdateThread(AreaList.this, false, LocationService.getLocation(AreaList.this));
					thread2.getObservable().addObserver(AreaList.this);
					
					thread2.start();
					break;
				case MESSAGE_NO_RESPONSE_TAG:
					AreaList.this.dismissDialog(DIALOG_DOWNLOAD_TAGS);
					AreaList.this.showDialog(DIALOG_NO_RESPONSE_TAG);
					break;
				case MESSAGE_CANCEL:
					AreaList.this.finish();
					break;
			}
		}
	};

	public void update(Observable observable, Object data)
	{
		UpdateData update = (UpdateData) data;

		if(update.source.equals("AreaUpdateThread")) {
			if(update.allDone){
				this.handler.sendEmptyMessage(MESSAGE_COMPLETE_AREA);
			} else if(update.description.equals("timeout")) {
				this.handler.sendEmptyMessage(MESSAGE_TIMEOUT_AREA);
			}
		} else if(update.source.equals("TagUpdateThread")) {
			if(update.allDone){
				this.handler.sendEmptyMessage(MESSAGE_COMPLETE_TAG);
			} else if(update.description.equals("timeout")) {
				this.handler.sendEmptyMessage(MESSAGE_TIMEOUT_TAG);
			} else if(update.description.equals("no_response")) {
				this.handler.sendEmptyMessage(MESSAGE_NO_RESPONSE_TAG);
			}
		}
	}
}