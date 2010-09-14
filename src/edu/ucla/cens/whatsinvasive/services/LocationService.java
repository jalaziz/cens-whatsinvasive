package edu.ucla.cens.whatsinvasive.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import edu.ucla.cens.whatsinvasive.R;
import edu.ucla.cens.whatsinvasive.TagType;
import edu.ucla.cens.whatsinvasive.WhatsInvasive;
import edu.ucla.cens.whatsinvasive.data.ITagDatabase;
import edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback;
import edu.ucla.cens.whatsinvasive.data.TagDatabase;
import edu.ucla.cens.whatsinvasive.data.TagDatabase.AreaRow;
import edu.ucla.cens.whatsinvasive.tools.CustomHttpClient;
import edu.ucla.cens.whatsinvasive.tools.UpdateThread;

public class LocationService extends Service {
    private static final String TAG = "LocationService";

    private MonitorThread thread;

    private static Location m_lastLoc = null;

    private static LocationManager manager;

    private static GpsListener gpsListener;

    @Override
    public void onCreate() {
        thread = new MonitorThread(this);
        gpsListener = new GpsListener();
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        m_lastLoc = getLastKnownLocation(manager);
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
                gpsListener);
        thread.start();

    }

    private Location getLastKnownLocation(LocationManager manager) {
        Location gps = manager
                .getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location network = manager
                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        // If GPS is disabled, use the last network location
        if (gps == null) {
            return network;
        }

        // Use the last network location if it's been over an hour since the
        // last gps fix
        if (network != null && (network.getTime() > (gps.getTime() + 3600000))) {
            return network;
        } else {
            return gps;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final RemoteCallbackList<ITagDatabaseCallback> m_callbacks = new RemoteCallbackList<ITagDatabaseCallback>();

    private final ITagDatabase.Stub binder = new ITagDatabase.Stub() {
        public void registerCallback(ITagDatabaseCallback cb)
                throws RemoteException {
            if (cb != null) {
                m_callbacks.register(cb);
            }
        }

        public void unregisterCallback(ITagDatabaseCallback cb)
                throws RemoteException {
            if (cb != null) {
                m_callbacks.unregister(cb);
            }
        }
    };

    private void parkChanged() {
        final int callbacks = m_callbacks.beginBroadcast();

        for (int i = 0; i < callbacks; i++) {
            try {
                m_callbacks.getBroadcastItem(i).parkTitleUpdated(
                        LocationService.getParkTitle(LocationService.this));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        m_callbacks.finishBroadcast();
    }

    @Override
    public void onDestroy() {
        thread.exit();
        manager.removeUpdates(gpsListener);
        super.onDestroy();
    }

    private static float roundToKm(float meters) {
        int hundreds = Math.round(meters / 100);

        return hundreds / 10.0f;
    }

    public static void updateTags(Activity activity) {
        TagUpdateThread thread = new TagUpdateThread(activity.getBaseContext(),
                getParkId(activity.getBaseContext()));
        thread.start();
    }

    public static Location getLocation(Context context) {
        // Use the last known network location for now
        return m_lastLoc;
    }

    private class GpsListener implements LocationListener {

        public void onLocationChanged(Location arg0) {
            if (arg0 != null) {
                m_lastLoc = arg0;

                if (arg0.getAccuracy() < 300.0) {
                    manager.removeUpdates(this);
                }
            }
        }

        public void onProviderDisabled(String arg0) {
        }

        public void onProviderEnabled(String arg0) {
        }

        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        }
    }

    private class MonitorThread extends Thread {
        private final long UPDATE_INTERVAL_ON = 60000;

        private final long UPDATE_INTERVAL_OFF = 60000 * 60 * 12;

        private final Context context;

        public Boolean runThread = true;

        public MonitorThread(Context context) {
            this.context = context;
        }

        @Override
        public void run() {
            SharedPreferences preferences = context.getSharedPreferences(
                    WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
            // We don't want to update the tags every time this is run, maybe we
            // could use the
            // checkVersion() code to notify a user when a new tag list is out
            // as well, so they can manually
            // update on their own. Otherwise, if keep doing this, tag lists
            // will be blank when a user is in a very
            // low connectivity area and cna't update their tags.
            // updateTags(); //update tags when thread is first run

            while (runThread) {
                try {
                    updateDistances(getLocation(this.context));
                    
                    if (preferences.getBoolean("locationServiceOn", true)) {
                        Thread.sleep(UPDATE_INTERVAL_ON);
                    } else {
                        Thread.sleep(UPDATE_INTERVAL_OFF);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private void exit() {
            runThread = false;
        }

        private void updateDistances(Location location) {
            // Update distances to top 5 areas and check if #1 priority changed
            // If it changed change area automatically
            // android.location.Location.distanceBetween(startLatitude,
            // startLongitude, endLatitude, endLongitude, results)
            ArrayList<TagDatabase.AreaRow> areas = new ArrayList<TagDatabase.AreaRow>();

            if (location != null) {
                TagDatabase db = new TagDatabase(LocationService.this);
                db.openRead();

                Cursor c = db.getAreas();

                // Check the previous top 5 rows
                while (c.moveToNext() && c.getPosition() < 5) {
                    AreaRow row = new AreaRow();
                    row.id = c.getLong(c.getColumnIndex(TagDatabase.KEY_ID));
                    row.latitude = c.getDouble(c
                            .getColumnIndex(TagDatabase.KEY_LATITUDE));
                    row.longitude = c.getDouble(c
                            .getColumnIndex(TagDatabase.KEY_LONGITUDE));

                    areas.add(row);
                }

                c.close();

                for (int i = 0; i < areas.size(); i++) {
                    float distance = 0;

                    if (location != null) {
                        float[] results = new float[1];
                        android.location.Location.distanceBetween(location
                                .getLatitude(), location.getLongitude(), areas
                                .get(i).latitude, areas.get(i).longitude,
                                results);

                        distance = LocationService.roundToKm(results[0]);
                    }

                    areas.get(i).distance = distance;

                    db.updateAreaDistance(areas.get(i).id, distance);
                }

                db.close();
            }
        }
    }

    public static long getParkId(Context context) {
        long id = -1;
        TagDatabase db = new TagDatabase(context);
        SharedPreferences preferences = context.getSharedPreferences(
                WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);

        // first try to get park by location
        if (preferences.getBoolean("locationServiceOn", true)
                && m_lastLoc != null) {
            db.openRead();

            // Default to the top-most area
            Cursor c = db.getAreas();
            if (c.moveToNext()) {
                id = c.getLong(c.getColumnIndex(TagDatabase.KEY_ID));
            }

            c.close();
            db.close();
        }

        // no park has been found, then fall back on a fixed area
        if (id == -1) {
            // Look for a fixed area
            long fixedArea = preferences.getLong("fixedArea", -1);

            // Make sure that area still exists in the list
            db.openRead();
            if (fixedArea != -1 && db.getArea(fixedArea) != null)
                id = fixedArea;
            db.close();
        }

        return id;
    }

    public static String getParkTitle(Context context) {
        // Default to the top-most area
        long id = getParkId(context);

        if (id != -1) {
            TagDatabase db = new TagDatabase(context);
            db.openRead();

            AreaRow area = db.getArea(id);

            db.close();

            return area.title;
        } else {
            return null;
        }
    }

    public static class AreaUpdateThread extends TagUpdateThread {
        private final String AREA_URL = "phone/getareas.php";

        private final Location location;

        private boolean sync = false;

        public AreaUpdateThread(Context context, boolean sync, Location location) {
            super(context, -1);

            this.sync = sync;
            this.location = location;
        }

        @Override
        public void run() {
            UpdateData result = downloadAreas(location);
            
            if (sync && result.allDone) {
                SharedPreferences preferences = context.getSharedPreferences(
                        WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);

                // Default to the top-most area
                TagDatabase db = new TagDatabase(context);
                db.openRead();

                Cursor c = db.getAreas();

                long id = -1;

                if (c.moveToNext()) {
                    id = c.getLong(c.getColumnIndex(TagDatabase.KEY_ID));
                }

                c.close();

                if (!preferences.getBoolean("locationServiceOn", true)) {
                    // Look for a fixed area
                    long fixedArea = preferences.getLong("fixedArea", -1);

                    // Make sure that area still exists in the list
                    if (db.getArea(id) != null && fixedArea != -1)
                        id = fixedArea;
                }

                if (id != -1) {
                    Cursor tagsCursor = db.getTags(id, null);

                    if (tagsCursor.getCount() == 0)
                        result = downloadTags(id);
                    
                    tagsCursor.close();
                } else {
                    result = new UpdateData(
                                    context.getString(R.string.locationservice_no_park),
                                    true);
                }

                db.close();
            }
            
            this.setChanged(result);
        }

        private UpdateData downloadAreas(Location location) {
            // Download the list of all areas
            List<NameValuePair> qparams = new ArrayList<NameValuePair>();

            if (location != null) {
                qparams.add(new BasicNameValuePair("lat", String
                        .valueOf(location.getLatitude())));
                qparams.add(new BasicNameValuePair("lon", String
                        .valueOf(location.getLongitude())));
            }

            try {
                StringBuilder result = sendRequest(context
                        .getString(R.string.base_url), AREA_URL, qparams);

                if (result != null) {
                    // Once complete reset database table and re-populate
                    TagDatabase db = new TagDatabase(context);
                    db.openWrite();

                    db.clearAreas();

                    JSONArray array = null;
                    try {
                        array = new JSONArray(result.toString());
                    } catch (JSONException e1) {
                        e1.printStackTrace();
                    }

                    for (int i = 0; array != null && i < array.length(); i++) {
                        try {
                            JSONObject object = new JSONObject(array.get(i)
                                    .toString());

                            // {"id":`number`, "title":`text`,
                            // "latitude":`double`, "longitude":`double`,
                            // "distance":`double`}
                            TagDatabase.AreaRow area = new TagDatabase.AreaRow();
                            area.id = object.getLong("id");
                            area.title = object.getString("title");
                            area.latitude = object.getDouble("latitude");
                            area.longitude = object.getDouble("longitude");
                            area.distance = roundToKm((float) (object
                                    .getDouble("distance") * 1000.0f)); // distance

                            db.insertArea(area);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    db.close();
                    return new UpdateData(true);
                } else {
                    return new UpdateData("no_response", false);
                }
            } catch (SocketTimeoutException e2) {
                return new UpdateData("timeout", false);
            }
        }
    }

    public static class TagUpdateThread extends ThumbnailUpdateThread {
        private final long id;

        public TagUpdateThread(Context context, long id) {
            super(context);

            this.id = id;
        }

        @Override
        public void run() {
            UpdateData result = downloadTags(id);
            this.setChanged(result);
        }

        protected UpdateData downloadTags(long id) {
            UpdateData result = null;
            Queue<ThumbnailUpdateThread.Thumbnail> thumbQueue = new LinkedList<ThumbnailUpdateThread.Thumbnail>();
            TagDatabase db = new TagDatabase(context);
            db.openWrite();

            // Clear database table and re-populate
            db.clearTags(id);

            for (TagType t : TagType.values()) {
                result = downloadTags(id, t, db, thumbQueue);

                if (!result.allDone)
                    break;
            }

            db.close();

            if (result.allDone) {
                // Spawn download thumbnails thread
                this.setQueue(thumbQueue);
                this.downloadThumbnails();
            }

            return result;
        }

        private UpdateData downloadTags(long id, TagType type, TagDatabase db,
                Queue<ThumbnailUpdateThread.Thumbnail> thumbQueue) {
            // Toast.makeText(context,
            // "Attempting to refresh plant tag list...",
            // Toast.LENGTH_LONG).show();
            // Updates the currently selected area's tags
            List<NameValuePair> qparams = new ArrayList<NameValuePair>();
            qparams.add(new BasicNameValuePair("id", String.valueOf(id)));

            // TODO Get last updated from the area's table row
            // if(updated!=null)
            // qparams.add(new BasicNameValuePair("u", updated));

            try {
                StringBuilder result = sendRequest(context
                        .getString(R.string.base_url), type.url(), qparams);

                // If HTTP status is 304 do nothing
                if (result != null) {
                    // {"id":`number`,"tags":[`tag`,...]}
                    // where `tag` element:
                    // {"title":"","imageUrl":"","text":"","type":""}
                    // type element is optional

                    JSONArray tags = null;

                    try {
                        tags = new JSONArray(result.toString());
                    } catch (JSONException e1) {
                        e1.printStackTrace();
                    }

                    for (int i = 0; tags != null && i < tags.length(); i++) {
                        try {
                            JSONObject object = new JSONObject(tags.get(i)
                                    .toString());

                            UUID imageId = UUID.randomUUID();

                            TagDatabase.TagRow tag = new TagDatabase.TagRow();
                            tag.title = object.getString("title");
                            if(object.has("science"))
                                tag.scienceName = object.getString("science");
                            if(object.has("common"))
                                tag.commonNames = object.getString("common").split(",");
                            tag.text = object.getString("text");
                            tag.order = i;
                            tag.flags = object.optString("type");
                            tag.type = type;

                            // Enqueue image for download in other thread and
                            // create local path
                            ThumbnailUpdateThread.Thumbnail thumb = new ThumbnailUpdateThread.Thumbnail();
                            File file = new File(Environment.getExternalStorageDirectory(), 
                                    context.getString(R.string.files_path) + "thumbs/" + imageId + ".jpg");
                            thumb.path = file.getPath();
                            thumb.url = object.getString("imageUrl");

                            thumbQueue.add(thumb);

                            tag.imagePath = file.getPath();
                            tag.areaId = id;

                            db.insertTag(tag);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    return new UpdateData(true);
                } else {
                    return new UpdateData("no_response", false);
                }
            } catch (SocketTimeoutException e2) {
                return new UpdateData("timeout", false);
            }
        }
    }

    private static class ThumbnailUpdateThread extends UpdateThread {
        private Queue<Thumbnail> queue;

        public ThumbnailUpdateThread(Context context) {
            super(context);

            this.context = context;
        }

        @Override
        public void run() {
            downloadThumbnails();
        }

        public void setQueue(Queue<Thumbnail> queue) {
            this.queue = queue;
        }

        public void downloadThumbnails() {
            // Make sure output directory exists
            new File(Environment.getExternalStorageDirectory(), 
                    context.getString(R.string.files_path) + "thumbs/").mkdirs();

            while (!this.queue.isEmpty()) {
                Thumbnail thumb = this.queue.remove();

                // Download to temporary location
                URI uri = null;
                try {
                    uri = new URI(thumb.url);
                } catch (URISyntaxException e1) {
                    e1.printStackTrace();
                }

                HttpClient httpClient = new CustomHttpClient();
                HttpGet request = new HttpGet(uri);

                HttpResponse response = null;
                try {
                    response = httpClient.execute(request);

                    int status = response.getStatusLine().getStatusCode();

                    if (status == HttpStatus.SC_OK) {
                        response.getEntity().writeTo(
                                new FileOutputStream(thumb.path + ".tmp"));

                        // Rename temporary file when download is complete
                        File f = new File(thumb.path + ".tmp");
                        f.renameTo(new File(thumb.path));
                    } else {
                        // Something went wrong
                    }

                    response.getEntity().consumeContent();
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (java.net.SocketTimeoutException e) {

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public static class Thumbnail {
            String url;
            String path;
        }
    }
}
