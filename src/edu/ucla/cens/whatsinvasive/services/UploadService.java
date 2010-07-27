package edu.ucla.cens.whatsinvasive.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import edu.ucla.cens.whatsinvasive.R;
import edu.ucla.cens.whatsinvasive.WhatsInvasive;
import edu.ucla.cens.whatsinvasive.data.PhotoDatabase;
import edu.ucla.cens.whatsinvasive.data.PhotoDatabase.PhotoDatabaseRow;
import edu.ucla.cens.whatsinvasive.tools.CustomHttpClient;

public class UploadService extends Service {
	PhotoDatabase pdb = null;

	private final int UPLOAD_INTERVAL = 60000;
	private final String TAG = "UploadService";

	private final String LOCATION_UPLOAD_URL = "phone/upload_location.php";
	private final String IMAGE_UPLOAD_URL = "phone/upload_image_auth.php";
	
	private PostThread thread;
	
	@Override
	public void onCreate() {
		pdb = new PhotoDatabase(this);
		
		thread = new PostThread();
		thread.start();
		
		Log.d(TAG,"Starting UploadService");
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		thread.exit();
		
		Log.i(TAG,"Stopping UploadService");
		
		super.onDestroy();
	}

	private class PostThread extends Thread {

		public Boolean runThread = true;

		@Override
		public void run() {

			try {
				while (runThread) {;
					Thread.sleep(UPLOAD_INTERVAL);
					Log.d(TAG, "Running the thread");
					
                    boolean uploadSuccess = false;

					// list all trace files
					pdb.open();
					ArrayList<PhotoDatabaseRow> photoentries = pdb.fetchPendingPhotos(0);

					Log.d(TAG, "Unuploaded Points: "+ Integer.toString(photoentries.size()));
					
					// lets stop the Upload service if there is nothing to upload
					if(photoentries.size() == 0)
						stopSelf();
					
					// points we will actually submit using the default timeout
					ArrayList<PhotoDatabaseRow> toDelete = pdb.fetchUploadedPhotos(20);
					for(PhotoDatabaseRow row : toDelete){
						pdb.deletePhoto(row.rowValue);
						Log.d(TAG, "Deleted uploaded photo #"+ Float.toString(row.rowValue) + " from database.");
					}
					
					photoentries = pdb.fetchPendingPhotos();
					pdb.close();
					
					Log.d(TAG, "Points to submit: "+ Integer.toString(photoentries.size()));
					
					SharedPreferences preferences = UploadService.this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
			        String username = preferences.getString("username", null);
			        String password = preferences.getString("password", null);
			        
					for (int i = 0; i < photoentries.size(); i++) {
						PhotoDatabaseRow photoentry = photoentries.get(i);
						
						File file = null;
						if (photoentry.filenameValue != null)
							file = new File(photoentry.filenameValue);

						try {
							String url = LOCATION_UPLOAD_URL;
							
							if(file != null && file.exists())
								url = IMAGE_UPLOAD_URL;
							
							String deviceId = Settings.System.getString(UploadService.this.getContentResolver(), Settings.System.ANDROID_ID);
							
							if(deviceId == null) deviceId = "356996016219759";
							
							URI uri = createUri(url, deviceId, photoentry.tagsValue, photoentry.noteValue, photoentry.timeValue, photoentry.latValue, photoentry.lonValue, photoentry.areaValue, photoentry.amountValue, username, password);
							
							Log.d(TAG,"amount = "+ photoentry.amountValue);
							
							if (doPost(uri, photoentry.filenameValue)) {
								if (file != null && file.exists()) {
									file.delete();
								}
								
								pdb.open();
								pdb.updatePhotoUploaded(photoentry.rowValue);
								pdb.close();
								
								uploadSuccess = true;
							}
						} catch (IOException e) {
							Log.d(TAG, "threw an IOException for sending file.");
							e.printStackTrace();
						}
					}
					
					if (uploadSuccess) {
						NotificationManager manager = (NotificationManager) UploadService.this.getSystemService(NOTIFICATION_SERVICE);
						Notification notification = new Notification(android.R.drawable.stat_sys_upload_done, getString(R.string.upload_service_done1), System.currentTimeMillis());
						PendingIntent intent = PendingIntent.getActivity(UploadService.this, 0, new Intent(UploadService.this, WhatsInvasive.class), 0); 
						notification.setLatestEventInfo(UploadService.this, getString(R.string.upload_service_done2), getString(R.string.upload_service_done3), intent); 
						manager.notify(R.string.upload_notification, notification);
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void exit() {
			runThread = false;
		}
		
		private URI createUri(String url, String deviceid, String tags, String notes, String phototime, String photolat, String photolon, Long area, String photoamount, String username, String password){
			List<NameValuePair> qparams = new ArrayList<NameValuePair>();
			qparams.add(new BasicNameValuePair("deviceid", deviceid));
			qparams.add(new BasicNameValuePair("tag", tags));
			qparams.add(new BasicNameValuePair("notes", notes));
			qparams.add(new BasicNameValuePair("datetime", phototime +" UTC")); //TODO: Remove UTC for ISO 8601
			qparams.add(new BasicNameValuePair("latitude", photolat));
			qparams.add(new BasicNameValuePair("longitude", photolon));
			qparams.add(new BasicNameValuePair("area_id", Long.toString(area)));
			qparams.add(new BasicNameValuePair("amount", photoamount));
			qparams.add(new BasicNameValuePair("username", username));
			qparams.add(new BasicNameValuePair("password", password));
			
			
			URI uri;
			try {
				String base = UploadService.this.getString(R.string.base_url);	
				uri = URIUtils.createURI("http", base, 80, url, URLEncodedUtils.format(qparams, "UTF-8"), null);
			} catch (URISyntaxException e) {
				return null;
			}
			
			return uri;
		}

		private boolean doPost(URI uri, String filename)
				throws IOException {
			Log.d(TAG, "Attempting to send file.");
			Log.d(TAG, "Trying to post: \"" + uri.toString() + "\" with file " + filename);

			HttpClient httpClient = new CustomHttpClient();
			HttpPost request = new HttpPost(uri);
			
			if(filename != null){
				FileInputStream fis = new FileInputStream(filename);
				
				InputStreamEntity entity = new InputStreamEntity(fis, -1);
				entity.setChunked(true);
				entity.setContentType("image/jpeg");
				
				request.setEntity(entity);
	
				Log.d(TAG, "After setting entity");
			}

			HttpResponse response = httpClient.execute(request);

			Log.d(TAG, "Doing HTTP Reqest");

			int status = response.getStatusLine().getStatusCode();
			//Log.d(TAG, generateString(response.getEntity().getContent()));
			Log.d(TAG, "Status Message: " + status);
			
			if (status == HttpStatus.SC_OK) {
			    BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                String line = br.readLine();
        
                while(line != null) {
                    if(line.equals("UPLOADED_OK")) {
                        Log.d(TAG, "Sent file.");
                        return true;
                    }
                    line = br.readLine();
                }
                
				Log.d(TAG, "Upload Failed. Response was not from a WI server.");
				return false;
			} else {
				InputStream is = response.getEntity().getContent();
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				StringBuilder sb = new StringBuilder();

				String line = null;
				try {
					while ((line = reader.readLine()) != null) {
						sb.append(line + "\n");
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						is.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				Log.d(TAG, "File not sent. Response: "+ sb.toString());
				return false;
			}
		}
	}
}
