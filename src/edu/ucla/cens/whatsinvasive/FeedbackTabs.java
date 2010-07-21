package edu.ucla.cens.whatsinvasive;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.TabHost.TabSpec;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

import edu.ucla.cens.whatsinvasive.data.TagDatabase;
import edu.ucla.cens.whatsinvasive.data.TagDatabase.AreaRow;
import edu.ucla.cens.whatsinvasive.services.LocationService;
import edu.ucla.cens.whatsinvasive.tools.CustomHttpClient;

public class FeedbackTabs extends MapActivity {
	protected static final String TAG = "Feedback";

	private final String STATS_URL = "http://sm.whatsinvasive.com/phone/get_stats.php";

	private final int MESSAGE_NEW_DATA = 0;
	
	private final int MENU_CHANGE_PERSONAL = 0;
	private final int MENU_CHANGE_GLOBAL = 1;
	
	private final int DIALOG_LOADING = 0;
	private final int DIALOG_CHANGE_VIEW = 1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setTitle(R.string.title_feedback);
		
		setContentView(R.layout.feedbacktabs);
		TabHost tabs = (TabHost)findViewById(R.id.tabhost);
        tabs.setup(); 
        
        TabSpec tab_one = tabs.newTabSpec("tab_one_tab");
        tab_one.setContent(R.id.layout1);
        tab_one.setIndicator("Stats",getResources().getDrawable(android.R.drawable.ic_dialog_info));
        tabs.addTab(tab_one);
        
        TabSpec tab_two = tabs.newTabSpec("tab_two_tab");
        tab_two.setContent(R.id.layout2);
        tab_two.setIndicator("Map",getResources().getDrawable(android.R.drawable.ic_dialog_map));        
        tabs.addTab(tab_two);
         
        tabs.setCurrentTab(0);
		
		SharedPreferences preferences = this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
        
		// Gets feedback mode preferences (defaults to private)
		// Private mode = 0
		// Global mode = 1
		downloadStats(preferences.getInt("feedback_mode", MENU_CHANGE_PERSONAL));
	}

	private void downloadStats(final int mode) {
		this.showDialog(DIALOG_LOADING);
		
		Thread thread = new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				msg.what = MESSAGE_NEW_DATA;
				
				Bundle bundle = new Bundle();
				bundle.putString("status", "unknown");
				
				HttpClient httpClient = new CustomHttpClient();
				
				String url = STATS_URL;
				url += "?area_id=" + LocationService.getParkId(FeedbackTabs.this);
				
				//if(mode==0){
					SharedPreferences preferences = FeedbackTabs.this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
			        String username = preferences.getString("username", null);
			        String password = preferences.getString("password", null);
					
					url += "&username="+ username +"&password="+ password;
				//}
				Log.d("FeedbackTabs.class URL", "URL VALUE:" + url);
				HttpGet request = new HttpGet(url);

				try {
					HttpResponse response = httpClient.execute(request);
					
					if(response.getStatusLine().getStatusCode()==HttpStatus.SC_OK){
						HttpEntity entity = response.getEntity();
						InputStream is = entity.getContent();
	
						BufferedReader reader = new BufferedReader(new InputStreamReader(is));
						StringBuilder sb = new StringBuilder();
	
						String line = null;
						try {
							while ((line = reader.readLine()) != null) {
								sb.append(line + "\n");
							}
							
							bundle.putString("status", "ok");
							bundle.putString("data", sb.toString());
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							try {
								is.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}else{
						// Failed to get data
					}
				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				bundle.putInt("mode", mode);
				
				msg.setData(bundle);
				
				FeedbackTabs.this.handler.sendMessage(msg);
			}
		};

		thread.start();
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		
		switch(id){
			case DIALOG_LOADING:
				ProgressDialog progress = new ProgressDialog(this);
				
				progress.setTitle(getString(R.string.login_loading));
				progress.setMessage(getString(R.string.feedback_tabs_downloading));
				progress.setIndeterminate(true);
				progress.setCancelable(false);
				
				dialog = progress;
				break;

		}
			
		return dialog;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_CHANGE_PERSONAL, 0, getString(R.string.feedback_tabs_my) + LocationService.getParkTitle(FeedbackTabs.this)).setIcon(android.R.drawable.ic_menu_view);
		menu.add(1, MENU_CHANGE_GLOBAL, 1, getString(R.string.feedback_tabs_all) + LocationService.getParkTitle(FeedbackTabs.this)).setIcon(android.R.drawable.ic_menu_view);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		SharedPreferences preferences = FeedbackTabs.this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);

		switch(item.getItemId()){
			case MENU_CHANGE_PERSONAL:


				preferences.edit().putInt("feedback_mode", MENU_CHANGE_PERSONAL).commit();

				FeedbackTabs.this.downloadStats(MENU_CHANGE_PERSONAL);

				
				break;
			case MENU_CHANGE_GLOBAL:
				preferences.edit().putInt("feedback_mode", MENU_CHANGE_GLOBAL).commit();

				FeedbackTabs.this.downloadStats(MENU_CHANGE_GLOBAL);
				break;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	private final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg){
			switch(msg.what){
				case MESSAGE_NEW_DATA:
					String result = msg.getData().getString("data");
					int mode = msg.getData().getInt("mode");
					
					try {
						// Extract data from JSON response
						// 
						// Sample data:
						// - Personal
						// "num_user_images":"0" *
						// "num_user_contributions":"0" *
						// "user_species_breakdown":{} *
						// "rank":13 *
						// "total_num_images":1061
						// "num_users":16
						// "top_users":[{"user":"tsagar","count":462},{"user":"inyo395","count":350},{"user":"thecleanmachine","count":101}]}
						// - Global
						// "total_num_images":1061
						// "num_users":16
						// "top_users":[{"user":"tsagar","count":462},{"user":"inyo395","count":350},{"user":"thecleanmachine","count":101}]}
						
						StatisticsData data = new StatisticsData();
						
						JSONObject json = new JSONObject(new JSONTokener(result));
						
						data.totalImages = json.getInt("total_num_images");	
						data.totalContributions = json.getInt("num_total_contributions");
						data.users = json.getInt("num_users");
						
						// Convert JSON for top users to Vector
						JSONArray top = json.getJSONArray("top_users");

						data.topUsers = new Vector<Object[]>();
						
						for(int i = 0; i < top.length(); i++){
							JSONObject user = top.getJSONObject(i);
							
							data.topUsers.add(i, new Object[]{user.get("user"), user.getInt("count")});
						}
						data.lastFiveSubmissions = new Vector<Object[]>();
						if(json.has("last_five_obs") && !json.getString("last_five_obs").equals("[]")){
							JSONArray observations = json.getJSONArray("last_five_obs");
												
							for(int i = 0; i < observations.length(); i++){
								JSONObject obs = observations.getJSONObject(i);
								
								data.lastFiveSubmissions.add(i, new Object[]{obs.get("latitude"), obs.get("longitude"),obs.get("maintag"),obs.get("id")});
							}
						}
						data.lastFiveGlobalSubmissions = new Vector<Object[]>();
						if(json.has("last_five_global_obs") && !json.getString("last_five_global_obs").equals("[]")){
							JSONArray observations = json.getJSONArray("last_five_global_obs");
												
							for(int i = 0; i < observations.length(); i++){
								JSONObject obs = observations.getJSONObject(i);
								
								data.lastFiveGlobalSubmissions.add(i, new Object[]{obs.get("latitude"), obs.get("longitude"),obs.get("maintag"),obs.get("id")});
							}
						}
						data.totalSpecies = new Vector<Object[]>();
						if(json.has("total_species_breakdown") && !json.getString("total_species_breakdown").equals("[]")){
							JSONObject species = json.getJSONObject("total_species_breakdown");
							
							
							Iterator<?> iterator = species.keys();
							while (iterator.hasNext()) {
						        String key = (String) iterator.next();
							
						        data.totalSpecies.add(new Object[]{key, species.getInt(key)});
							}
						}
						
						//if(mode==0){
							// Pull out the personal specific data
							data.userImages = json.getInt("num_user_images");
							
							if(!json.getString("rank").equalsIgnoreCase("user not yet ranked"))
								data.userRank = json.getInt("rank");
							else
								data.userRank = -1;
							
							data.userContributions = json.getInt("num_user_contributions");
							data.userSpecies = new Vector<Object[]>();
							if(json.has("user_species_breakdown") && !json.getString("user_species_breakdown").equals("[]")){
								JSONObject species = json.getJSONObject("user_species_breakdown");
								
								
								Iterator<?> iterator = species.keys();
								while (iterator.hasNext()) {
							        String key = (String) iterator.next();
								
							        data.userSpecies.add(new Object[]{key, species.getInt(key)});
								}
							}
							
						//}
						
						// Extract the latest uploaded items
						/*JSONArray latest = json.optJSONArray("last_five_obs");
						
						if(latest!=null){
							latestUploads = new Vector<HashMap<String, Object>>();
							
							for(int i = 0; i < latest.length(); i++){
								JSONObject data = latest.getJSONObject(i);
								
								HashMap<String, Object> entry = new HashMap<String, Object>();
								entry.put("timestamp", data.get("dttaken"));
								entry.put("tags", data.get("tags"));
								entry.put("latitude", data.getDouble("latitude"));
								entry.put("longitude", data.getDouble("longitude"));
								entry.put("squareimg", data.get("squareimg"));
								entry.put("thumbimg", data.get("squareimg"));
								entry.put("smallimg", data.get("squareimg"));
								entry.put("mediumimg", data.get("squareimg"));
								entry.put("originalimg", data.get("squareimg"));
								
								latestUploads.add(i, entry);
							}
						}*/
						
						updateDisplay(data, mode);
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					FeedbackTabs.this.dismissDialog(DIALOG_LOADING);
					
				break;
			}
		}
	};
	
	private void SwitchLayout2() {
		TextView title = (TextView) findViewById(R.id.TextViewTitle);
		title.setText(getString(R.string.feedback_tabs_my) + LocationService.getParkTitle(FeedbackTabs.this)+ getString(R.string.feedback_tabs_statistics));
		TextView title2 = (TextView) findViewById(R.id.TextViewTitle2);
		title2.setText(getString(R.string.feedback_tabs_my) + LocationService.getParkTitle(FeedbackTabs.this)+ getString(R.string.feedback_tabs_statistics));

		}

	private void SwitchLayout1() {
		
		TextView title = (TextView) findViewById(R.id.TextViewTitle);
		title.setText(getString(R.string.feedback_tabs_all) + LocationService.getParkTitle(FeedbackTabs.this)+ getString(R.string.feedback_tabs_statistics));
		TextView title2 = (TextView) findViewById(R.id.TextViewTitle2);
		title2.setText(getString(R.string.feedback_tabs_all) + LocationService.getParkTitle(FeedbackTabs.this)+ getString(R.string.feedback_tabs_statistics));		
		}
		
	private void updateDisplay(StatisticsData data, int mode){
		if(mode==0){
	        SwitchLayout2();
	        
			TextView pests = (TextView) this.findViewById(R.id.mypests);
			TextView photos = (TextView) this.findViewById(R.id.myphotos);
			TextView rank = (TextView) this.findViewById(R.id.myrank);
			
			pests.setText(getString(R.string.feedback_tabs_invasives) + String.valueOf(data.userContributions));
			photos.setText(getString(R.string.feedback_tabs_photos) + String.valueOf(data.userImages));
			rank.setText(getString(R.string.feedback_tabs_rank) + ((data.userRank==-1) ? "-" : String.valueOf(data.userRank)));
			
			TextView pests2 = (TextView) this.findViewById(R.id.TextView01);
			TextView photos2 = (TextView) this.findViewById(R.id.TextView02);			
			TextView top = (TextView) this.findViewById(R.id.TextView03);
			TextView top2 = (TextView) this.findViewById(R.id.topusers1);
			TextView rank2 = (TextView) this.findViewById(R.id.myrank2);
			rank2.setText(getString(R.string.feedback_tabs_rank) + ((data.userRank==-1) ? "-" : String.valueOf(data.userRank)));
			pests2.setText(getString(R.string.feedback_tabs_invasives) + String.valueOf(data.userContributions));
			photos2.setText(getString(R.string.feedback_tabs_photos) + String.valueOf(data.userImages));
			top.setText("");
			top2.setText("");
			
			/*			
			PhotoDatabase pdb = new PhotoDatabase(this);
			pdb.open();
			
			ArrayList<PhotoDatabaseRow> uploaded = pdb.fetchUploadedPhotos(5);
			
			pdb.close();
			*/
			if(data.lastFiveSubmissions.size()>0){
				MapView map = (MapView) this.findViewById(R.id.MapView01);
				map.getController().setZoom(10);
				//set to center of california
				TagDatabase tdb = new TagDatabase(this);
				tdb.openRead();							
				AreaRow area = tdb.getArea(LocationService.getParkId(this));			
				Log.d("FeedbackTabs.class",getString(R.string.feedback_tabs_park_id) + LocationService.getParkId(this));
				Double latitude = area.latitude * 1E6;
				Log.d("FeedbackTabs.class",getString(R.string.feedback_tabs_latitude) + latitude);
				
				Double longitude = area.longitude * 1E6;
				Log.d("FeedbackTabs.class",getString(R.string.feedback_tabs_longitude) + longitude);
				tdb.close();
				map.getController().setCenter(new GeoPoint(latitude.intValue(),longitude.intValue()));
				map.setBuiltInZoomControls(true);
				
				List<Overlay> overlays = map.getOverlays();
				overlays.clear();
				
				CustomItemizedOverlay overlay = new CustomItemizedOverlay(this.getResources().getDrawable(R.drawable.btn_rating_star_off_pressed));
				
				for(int i = 0; i < data.lastFiveSubmissions.size(); i++)
				{
					//PhotoDatabaseRow row = uploaded.get(i);
					Object[] obsDetails = data.lastFiveSubmissions.get(i);
					Double lat = Double.parseDouble(obsDetails[0].toString()) * 1E6;
					Double lon = Double.parseDouble(obsDetails[1].toString()) * 1E6;
					
					GeoPoint point = new GeoPoint(lat.intValue(), lon.intValue());
					CustomOverlayItem item = new CustomOverlayItem(point, obsDetails[2].toString(), "");
					item.id = Long.parseLong(obsDetails[3].toString());
					
					overlay.addOverlay(item);
				}
				
				overlays.add(overlay);
			}
			/*	
				TextView weeds = (TextView) this.findViewById(R.id.TextView01);
				TextView photos = (TextView) this.findViewById(R.id.TextView02);
				TextView top = (TextView) this.findViewById(R.id.TextView03);
				
				weeds.setText("Weeds: " + String.valueOf(data.totalContributions));
				photos.setText("Photos: " + String.valueOf(data.totalImages));
				
				StringBuilder sb = new StringBuilder();
				
				for(int i = 0; i < data.topUsers.size(); i++)
				{
					Object[] details = data.topUsers.get(i);
					
					if(i > 0) sb.append("\n");
					
					sb.append((i + 1) +". "+ details[0].toString() +" ("+ details[1].toString() +")");
				}
		
				top.setText("Top collectors:\n"+ sb.toString());
				*/
				
				StringBuilder names = new StringBuilder();
				StringBuilder values = new StringBuilder();
				
//				String[] tags = this.getResources().getStringArray(R.array.tags);
				
				int max = 0;
				int rows = 0;
				String[] vals = new String[data.userSpecies.size()];
				for(int i = 0; i < data.userSpecies.size(); i++){
					Object[] entries = data.userSpecies.get(i);
					/*
					boolean accept = false;
					
					// Filter out anything that's not in the local tag list	
					for(int j = 0; j < tags.length; j++){
						if(tags[j].equalsIgnoreCase(entries[0].toString())){
							accept = true;
							break;
						}
					}
					
					if(!accept) continue;
					*/
					if(names.length() > 0){
						names.append("|");
						values.append(",");
					}
					
					max = Math.max(Integer.parseInt(entries[1].toString()), max);
					
					names.append(URLEncoder.encode(entries[0].toString()));
					values.append(entries[1]);
					if(!entries[0].equals(null))
					vals[i] = entries[0].toString();
					
					rows++;
				}
				// Switch around the names so as to abide by google chart input parameters format...lame.
				names = new StringBuilder();
				for (int i = vals.length -1 ; i >= 0;i-- ){
					if(i < vals.length - 1){
						names.append("|");
					}
					names.append(URLEncoder.encode(vals[i]));
					
				}
				 
				max = (max/10 + 1) * 10;
				int height = 18 + (28 * rows);
				
				ImageView chart = (ImageView) this.findViewById(R.id.ImageView01);
				 
				String url = "http://chart.apis.google.com/chart?cht=bhs&chd=t:"+ values.toString() +"&chco=66cc33&chs=300x"+ height +"&chxr=1,0,"+ max +","+ (max/2) +"&chds=0," + max + "&chxt=y,x&chxl=0:|"+ names.toString();
				
				chart.setImageBitmap(getImageBitmap(url));
						
			
		}else{
			SwitchLayout1();
			// http://chart.apis.google.com/chart?cht=p&chd=t:60,40&chs=250x100&chl=Hello|World
			/*
			TextView weeds = (TextView) this.findViewById(R.id.myweeds);
			TextView photos = (TextView) this.findViewById(R.id.myphotos);
			TextView rank = (TextView) this.findViewById(R.id.myrank);
			
			weeds.setText("Weeds: " + String.valueOf(data.userContributions));
			photos.setText("Photos: " + String.valueOf(data.userImages));
			rank.setText("Rank: " + ((data.userRank==-1) ? "-" : String.valueOf(data.userRank)));
			*/
			/*			
			PhotoDatabase pdb = new PhotoDatabase(this);
			pdb.open();
			
			ArrayList<PhotoDatabaseRow> uploaded = pdb.fetchUploadedPhotos(5);
			
			pdb.close();
			*/
			if(data.lastFiveGlobalSubmissions.size()>0){
				MapView map = (MapView) this.findViewById(R.id.MapView01);
				map.getController().setZoom(10);
				//set to center of california
				TagDatabase tdb = new TagDatabase(this);
				tdb.openRead();							
				AreaRow area = tdb.getArea(LocationService.getParkId(this));			
				Log.d("FeedbackTabs.class",getString(R.string.feedback_tabs_park_id) + LocationService.getParkId(this));
				Double latitude = area.latitude * 1E6;
				Log.d("FeedbackTabs.class",getString(R.string.feedback_tabs_latitude) + latitude);
				
				Double longitude = area.longitude * 1E6;
				Log.d("FeedbackTabs.class",getString(R.string.feedback_tabs_longitude) + longitude);
				tdb.close();
				map.getController().setCenter(new GeoPoint(latitude.intValue(),longitude.intValue()));
				map.setBuiltInZoomControls(true);
				
				List<Overlay> overlays = map.getOverlays();
				overlays.clear();
				
				CustomItemizedOverlay overlay = new CustomItemizedOverlay(this.getResources().getDrawable(R.drawable.btn_rating_star_off_pressed));
				
				for(int i = 0; i < data.lastFiveGlobalSubmissions.size(); i++)
				{
					//PhotoDatabaseRow row = uploaded.get(i);
					Object[] obsDetails = data.lastFiveGlobalSubmissions.get(i);
					Double lat = Double.parseDouble(obsDetails[0].toString()) * 1E6;
					Double lon = Double.parseDouble(obsDetails[1].toString()) * 1E6;
					
					GeoPoint point = new GeoPoint(lat.intValue(), lon.intValue());
					CustomOverlayItem item = new CustomOverlayItem(point, obsDetails[2].toString(), "");
					item.id = Long.parseLong(obsDetails[3].toString());
					
					overlay.addOverlay(item);
				}
				
				overlays.add(overlay);			
			}
			TextView pests = (TextView) this.findViewById(R.id.mypests);
			TextView photos = (TextView) this.findViewById(R.id.myphotos);
			TextView rank = (TextView) this.findViewById(R.id.myrank);
			
			pests.setText(getString(R.string.feedback_tabs_invasives) + String.valueOf(data.totalContributions));
			photos.setText(getString(R.string.feedback_tabs_photos) + String.valueOf(data.totalImages));
			rank.setText("");
			
			TextView pests2 = (TextView) this.findViewById(R.id.TextView01);
			TextView photos2 = (TextView) this.findViewById(R.id.TextView02);
			TextView top = (TextView) this.findViewById(R.id.TextView03);
			TextView top2 = (TextView) this.findViewById(R.id.topusers1);
			TextView rank2 = (TextView) this.findViewById(R.id.myrank2);
			rank2.setText("");
			pests2.setText(getString(R.string.feedback_tabs_invasives) + String.valueOf(data.totalContributions));
			photos2.setText(getString(R.string.feedback_tabs_photos) + String.valueOf(data.totalImages));
			
			StringBuilder sb = new StringBuilder();
			
			for(int i = 0; i < data.topUsers.size(); i++)
			{
				Object[] details = data.topUsers.get(i);
				
				if(i > 0) sb.append("\n");
				
				sb.append((i + 1) +". "+ details[0].toString() +" ("+ details[1].toString() +")");
			}
	
			top.setText(getString(R.string.feedback_tabs_top) + sb.toString());			
			top2.setText(getString(R.string.feedback_tabs_top) + sb.toString());
			
			StringBuilder names = new StringBuilder();
			StringBuilder values = new StringBuilder();
			
//			String[] tags = this.getResources().getStringArray(R.array.tags);
			
			int max = 0;
			int rows = 0;
			String[] vals = new String[data.totalSpecies.size()];
			for(int i = 0; i < data.totalSpecies.size(); i++){
				Object[] entries = data.totalSpecies.get(i);
				/*
				boolean accept = false;
				
				// Filter out anything that's not in the local tag list	
				for(int j = 0; j < tags.length; j++){
					if(tags[j].equalsIgnoreCase(entries[0].toString())){
						accept = true;
						break;
					}
				}
				
				if(!accept) continue;
				*/
				if(names.length() > 0){
					names.append("|");
					values.append(",");
				}
				
				max = Math.max(Integer.parseInt(entries[1].toString()), max);
				
				names.append(URLEncoder.encode(entries[0].toString()));
				values.append(entries[1]);
				if(!entries[0].equals(null))
				vals[i] = entries[0].toString();
				
				rows++;
			}
			// Switch around the names so as to abide by google chart input parameters format...lame.
			names = new StringBuilder();
			for (int i = vals.length -1 ; i >= 0;i-- ){
				if(i < vals.length - 1){
					names.append("|");
				}
				names.append(URLEncoder.encode(vals[i]));
				
			}
			 
			max = (max/10 + 1) * 10;
			int height = 18 + (28 * rows);
			
			ImageView chart = (ImageView) this.findViewById(R.id.ImageView01);
			 
			String url = "http://chart.apis.google.com/chart?cht=bhs&chd=t:"+ values.toString() +"&chco=66cc33&chs=300x"+ height +"&chxr=1,0,"+ max +","+ (max/2) +"&chds=0," + max + "&chxt=y,x&chxl=0:|"+ names.toString();
			
			chart.setImageBitmap(getImageBitmap(url));
		}
	}
	
	private Bitmap getImageBitmap(String url){ 
        Bitmap bm = null; 
        try { 
            URL aURL = new URL(url); 
            URLConnection conn = aURL.openConnection(); 
            conn.connect(); 
            InputStream is = conn.getInputStream(); 
            BufferedInputStream bis = new BufferedInputStream(is); 
            bm = BitmapFactory.decodeStream(bis); 
            bis.close(); 
            is.close(); 
       } catch (IOException e) { 
       } 
       return bm; 
    } 
	
	private class StatisticsData{
		public int users;
		private Vector<Object[]> topUsers;
		private Vector<Object[]> lastFiveSubmissions;
		private Vector<Object[]> lastFiveGlobalSubmissions;
		public int totalImages;
		public int totalContributions;
		private Vector<Object[]> totalSpecies;

		public int userImages;
		public int userRank;
		public int userContributions;
		private Vector<Object[]> userSpecies;
		
		//private Vector<HashMap<String, Object>> latestUploads;
	}
	
	private class CustomOverlayItem extends OverlayItem {
		public long id;
		
		public CustomOverlayItem(GeoPoint point, String title, String snippet) {
			super(point, title, snippet);
			// TODO Auto-generated constructor stub
		}
	}
	
	private class CustomItemizedOverlay extends ItemizedOverlay<OverlayItem> {		
		public CustomItemizedOverlay(Drawable defaultMarker) {
			super(boundCenterBottom(defaultMarker));
		}

		private final ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
		
		public void addOverlay(OverlayItem overlay) {
		    mOverlays.add(overlay);
		    populate();
		}
		
		@Override
		protected OverlayItem createItem(int i) {
			return mOverlays.get(i);
		}

		@Override
		public int size() {
			return mOverlays.size();
		}

		@Override
		public boolean onTap(int index) {
			/*
			CustomOverlayItem item = (CustomOverlayItem) this.getItem(index);
			
			Intent intent = new Intent(FeedbackTabs.this, ViewTag.class);
			intent.putExtra("id", (int) item.id);
			
			FeedbackTabs.this.startActivity(intent);
			*/
			return super.onTap(index);
		}
		
	}

	@Override
	protected boolean isRouteDisplayed() {
		// TODO Auto-generated method stub
		return false;
	}
}
