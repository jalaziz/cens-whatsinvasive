package edu.ucla.cens.whatsinvasive;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Iterator;
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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.TabHost.OnTabChangeListener;
import edu.ucla.cens.whatsinvasive.services.LocationService;
import edu.ucla.cens.whatsinvasive.tools.CustomHttpClient;

public class FeedbackTabs extends TabActivity {
    protected static final String TAG = "Feedback";

    private final String STATS_URL = "http://sm.whatsinvasive.com/phone/get_stats.php";

    private final int MESSAGE_NEW_DATA = 0;
    
    private final int MESSAGE_TIMEOUT = 1;
    
    private final int MESSAGE_BAD_RESPONSE = 2;

    private final int DIALOG_LOADING = 0;
    
    private final int DIALOG_TIMEOUT = 1;
    
    private final int DIALOG_BAD_RESPONSE = 2;

    private final int PERSONAL_STATS = 0;

    private final int GLOBAL_STATS = 1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_feedback);
        setContentView(R.layout.feedbacktabs);
        
        Resources res = getResources();
        TabHost tabHost = getTabHost();
        TabHost.TabSpec spec;

        spec = tabHost.newTabSpec(Integer.toString(PERSONAL_STATS));
        spec.setContent(R.id.personal);
        spec.setIndicator(getString(R.string.feedback_tabs_personal), res.getDrawable(R.drawable.chart));
        tabHost.addTab(spec);

        spec = tabHost.newTabSpec(Integer.toString(GLOBAL_STATS));
        spec.setContent(R.id.global);
        spec.setIndicator(getString(R.string.feedback_tabs_global), res.getDrawable(R.drawable.globe));
        tabHost.addTab(spec);
        
        tabHost.setOnTabChangedListener(new OnTabChangeListener() {
            public void onTabChanged(String tabId) {
                downloadStats(Integer.parseInt(tabId));
            }
        });

        tabHost.setCurrentTab(PERSONAL_STATS);
        downloadStats(PERSONAL_STATS);
    }

    private void downloadStats(final int mode) {
        this.showDialog(DIALOG_LOADING);

        Thread thread = new Thread() {
            @Override
            public void run() {
                HttpClient httpClient = new CustomHttpClient();

                String url = STATS_URL;
                url += "?area_id="
                        + LocationService.getParkId(FeedbackTabs.this);

                SharedPreferences preferences = FeedbackTabs.this
                        .getSharedPreferences(WhatsInvasive.PREFERENCES_USER,
                                Activity.MODE_PRIVATE);
                String username = preferences.getString("username", null);
                String password = preferences.getString("password", null);

                url += "&username=" + username + "&password=" + password;

                Log.d("FeedbackTabs.class URL", "URL VALUE:" + url);
                HttpGet request = new HttpGet(url);

                try {
                    HttpResponse response = httpClient.execute(request);

                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        Bundle bundle = new Bundle();
                        bundle.putString("status", "unknown");
                        bundle.putInt("mode", mode);
                        
                        HttpEntity entity = response.getEntity();
                        InputStream is = entity.getContent();

                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(is));
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
                        
                        Message msg = new Message();
                        msg.what = MESSAGE_NEW_DATA;
                        msg.setData(bundle);
                        FeedbackTabs.this.handler.sendMessage(msg);
                    } else {
                        // Failed to get data
                        FeedbackTabs.this.handler.sendEmptyMessage(MESSAGE_BAD_RESPONSE);
                    }
                } catch (ClientProtocolException e) {
                    FeedbackTabs.this.handler.sendEmptyMessage(MESSAGE_TIMEOUT);
                    e.printStackTrace();
                } catch (SocketTimeoutException e) {
                    FeedbackTabs.this.handler.sendEmptyMessage(MESSAGE_TIMEOUT);
                } catch (IOException e) {
                    FeedbackTabs.this.handler.sendEmptyMessage(MESSAGE_TIMEOUT);
                    e.printStackTrace();
                }
            }
        };

        thread.start();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;

        switch (id) {
        case DIALOG_LOADING:
            ProgressDialog progress = new ProgressDialog(this);

            progress.setTitle(getString(R.string.login_loading));
            progress.setMessage(getString(R.string.feedback_tabs_downloading));
            progress.setIndeterminate(true);
            progress.setCancelable(false);

            dialog = progress;
            
            break;
        case DIALOG_BAD_RESPONSE:
        case DIALOG_TIMEOUT:
            dialog = new AlertDialog.Builder(this)
            .setTitle(getString(R.string.feedback_timeout))
            .setMessage(getString(R.string.feedback_fail))
            .setPositiveButton(android.R.string.ok, null)
            .create();
            
            break;
        }

        return dialog;
    }

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_BAD_RESPONSE:
            case MESSAGE_TIMEOUT:
                FeedbackTabs.this.dismissDialog(DIALOG_LOADING);
                FeedbackTabs.this.showDialog(DIALOG_TIMEOUT);
                break;
            case MESSAGE_NEW_DATA:
                String result = msg.getData().getString("data");
                int mode = msg.getData().getInt("mode");

                try {
                    StatisticsData data = new StatisticsData();

                    JSONObject json = new JSONObject(new JSONTokener(result));

                    data.totalImages = json.getInt("total_num_images");
                    data.totalContributions = json.getInt("num_total_contributions");
                    data.users = json.getInt("num_users");

                    // Convert JSON for top users to Vector
                    JSONArray top = json.getJSONArray("top_users");

                    data.topUsers = new Vector<Object[]>();

                    for (int i = 0; i < top.length(); i++) {
                        JSONObject user = top.getJSONObject(i);

                        data.topUsers.add(i, new Object[] { user.get("user"),
                                user.getInt("count") });
                    }

                    data.lastFiveSubmissions = new Vector<Object[]>();
                    if (json.has("last_five_obs")
                            && !json.getString("last_five_obs").equals("[]")) {
                        JSONArray observations = json.getJSONArray("last_five_obs");

                        for (int i = 0; i < observations.length(); i++) {
                            JSONObject obs = observations.getJSONObject(i);

                            data.lastFiveSubmissions.add(i, new Object[] {
                                    obs.get("latitude"), obs.get("longitude"),
                                    obs.get("maintag"), obs.get("id") });
                        }
                    }

                    data.lastFiveGlobalSubmissions = new Vector<Object[]>();
                    if (json.has("last_five_global_obs")
                            && !json.getString("last_five_global_obs").equals("[]")) {
                        JSONArray observations = json.getJSONArray("last_five_global_obs");

                        for (int i = 0; i < observations.length(); i++) {
                            JSONObject obs = observations.getJSONObject(i);

                            data.lastFiveGlobalSubmissions.add(i, new Object[] {
                                    obs.get("latitude"), obs.get("longitude"),
                                    obs.get("maintag"), obs.get("id") });
                        }
                    }

                    data.totalSpecies = new Vector<Object[]>();
                    if (json.has("total_species_breakdown")
                            && !json.getString("total_species_breakdown").equals("[]")) {
                        JSONObject species = json.getJSONObject("total_species_breakdown");

                        Iterator<?> iterator = species.keys();
                        while (iterator.hasNext()) {
                            String key = (String) iterator.next();

                            data.totalSpecies.add(new Object[] { key,
                                    species.getInt(key) });
                        }
                    }

                    // Pull out the personal specific data
                    data.userImages = json.getInt("num_user_images");

                    if (!json.getString("rank").equalsIgnoreCase("user not yet ranked"))
                        data.userRank = json.getInt("rank");
                    else
                        data.userRank = -1;

                    data.userContributions = json.getInt("num_user_contributions");
                    data.userSpecies = new Vector<Object[]>();
                    if (json.has("user_species_breakdown")
                            && !json.getString("user_species_breakdown").equals("[]")) {
                        JSONObject species = json.getJSONObject("user_species_breakdown");

                        Iterator<?> iterator = species.keys();
                        while (iterator.hasNext()) {
                            String key = (String) iterator.next();

                            data.userSpecies.add(new Object[] { key,
                                    species.getInt(key) });
                        }
                    }

                    updateDisplay(data, mode);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                FeedbackTabs.this.dismissDialog(DIALOG_LOADING);

                break;
            }
        }
    };

    private void updateDisplay(StatisticsData data, int mode) {
        String parkTitle = LocationService.getParkTitle(this);
        View tab;
        Vector<Object[]> species;
        int contributions;
        int images;
        
        if (mode == PERSONAL_STATS) {
            tab = this.findViewById(R.id.personal);
            TextView title = (TextView) tab.findViewById(R.id.title);
            title.setText(getString(R.string.feedback_tabs_my) + " " 
                    + parkTitle + " " + getString(R.string.feedback_tabs_statistics));
            
            contributions = data.userContributions;
            images = data.userImages;
            species = data.userSpecies;
            
            TextView rank = (TextView) tab.findViewById(R.id.rank);
            rank.setVisibility(View.VISIBLE);
            rank.setText(getString(R.string.feedback_tabs_rank) + " "
                    + ((data.userRank == -1) ? "-" : String
                            .valueOf(data.userRank)));
        } else {
            tab = this.findViewById(R.id.global);
            TextView title = (TextView) tab.findViewById(R.id.title);
            title.setText(getString(R.string.feedback_tabs_all) + " " 
                    + parkTitle + " " + getString(R.string.feedback_tabs_statistics));
            
            contributions = data.totalContributions;
            images = data.totalImages;
            species = data.totalSpecies;

            TextView top = (TextView) tab.findViewById(R.id.topusers);
            top.setVisibility(View.VISIBLE);

            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < data.topUsers.size(); i++) {
                Object[] details = data.topUsers.get(i);

                if (i > 0)
                    sb.append("\n");

                sb.append((i + 1) + ". " + details[0].toString() + " ("
                        + details[1].toString() + ")");
            }

            top.setText(getString(R.string.feedback_tabs_top) + "\n" + sb.toString());
        }
        
        TextView pests = (TextView) tab.findViewById(R.id.pests);
        TextView photos = (TextView) tab.findViewById(R.id.photos);
        pests.setText(getString(R.string.feedback_tabs_invasives)
                + " " + String.valueOf(contributions));
        photos.setText(getString(R.string.feedback_tabs_photos)
                + " " + String.valueOf(images));
        
        StringBuilder names = new StringBuilder();
        StringBuilder values = new StringBuilder();

        int max = 0;
        int rows = 0;
        String[] vals = new String[species.size()];
        for (int i = 0; i < species.size(); i++) {
            Object[] entries = species.get(i);

            if (names.length() > 0) {
                names.append("|");
                values.append(",");
            }

            max = Math.max(Integer.parseInt(entries[1].toString()), max);

            names.append(URLEncoder.encode(entries[0].toString()));
            values.append(entries[1]);
            if (!entries[0].equals(null))
                vals[i] = entries[0].toString();

            rows++;
        }
        
        // Switch around the names so as to abide by google chart input
        // parameters format...lame.
        names = new StringBuilder();
        for (int i = vals.length - 1; i >= 0; i--) {
            if (i < vals.length - 1) {
                names.append("|");
            }
            names.append(URLEncoder.encode(vals[i]));

        }

        max = (max / 10 + 1) * 10;
        int height = 18 + (28 * rows);

        ImageView chart = (ImageView) tab.findViewById(R.id.chart);

        String url = "http://chart.apis.google.com/chart?cht=bhs&chd=t:"
                + values.toString() + "&chco=66cc33&chs=300x" + height
                + "&chxr=1,0," + max + "," + (max / 2) + "&chds=0," + max
                + "&chxt=y,x&chxl=0:|" + names.toString();
        
        chart.setImageBitmap(getImageBitmap(url));
    }

    private Bitmap getImageBitmap(String url) {
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

    private class StatisticsData {
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
    }
}
