package edu.ucla.cens.whatsinvasive;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Observable;
import java.util.Observer;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import edu.ucla.cens.whatsinvasive.data.ITagDatabase;
import edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback;
import edu.ucla.cens.whatsinvasive.data.TagDatabase;
import edu.ucla.cens.whatsinvasive.services.LocationService;
import edu.ucla.cens.whatsinvasive.services.LocationService.TagUpdateThread;
import edu.ucla.cens.whatsinvasive.services.UploadService;
import edu.ucla.cens.whatsinvasive.tools.CustomHttpClient;
import edu.ucla.cens.whatsinvasive.tools.DebugCollector;
import edu.ucla.cens.whatsinvasive.tools.UpdateThread.UpdateData;

public class WhatsInvasive extends Activity implements Observer {
    private final int ACTIVITY_LOGIN = 0;

    private final int MENU_ABOUT = 0;

    private final int MENU_FEEDBACK = 1;

    private final int MENU_QUEUE = 2;

    private final int MENU_SETTINGS = 3;

    private final int MENU_HELP = 4;

    private static final int NO_UPDATE_AVAILABLE = 0;

    private static final int UPDATE_REQUIRED = 1;

    private static final int UPDATE_AVAILABLE = 2;

    private static Boolean UNINSTALL_REQUIRED = false;

    public static final String PREFERENCES_USER = "user";

    public static final int RESULT_LOCATION_DISABLED = 1;

    public static final int RESULT_LOCATION_MISSING = 2;

    private static final String TAG = "WhatsInvasive";

    private static final int MESSAGE_UPDATE_LOCATION = 60;

    private static final int MESSAGE_COMPLETE_TAG = 55;

    private static final int MESSAGE_TIMEOUT_TAG = 56;

    private static final int MESSAGE_NO_RESPONSE_TAG = 57;

    private static final int WELCOMER_RESULT = 58;

    protected static final int HELP_IMAGE = 59;

    protected static final int CHANGE_GPS_SETTINGS = 23;

    protected static final int CHANGE_GPS_SETTINGS_2 = 24;

    protected static final int BLOCKING_TAG = 312;

    private AlertDialog.Builder mAlertDialogBuilder;

    private ServiceConnection mConn;

    private SharedPreferences mPreferences;

    private final ITagDatabaseCallback tagDatabaseCallback = new ITagDatabaseCallback.Stub() {
        public void parkTitleUpdated(String title) throws RemoteException {
            Message msg = Message.obtain(handler);
            msg.what = MESSAGE_UPDATE_LOCATION;
            handler.sendMessage(msg);
        }
    };

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.arg1 == 5) {
                WhatsInvasive.this.dismissDialog(BLOCKING_TAG);
                onResume();
            } else {
                switch (msg.what) {
                case MESSAGE_UPDATE_LOCATION:
                    updatePark();
                    if (WhatsInvasive.this.hasWindowFocus()) {
                        showToast(getString(R.string.park_location_updated),
                                Toast.LENGTH_SHORT);
                    }
                    break;
                case MESSAGE_COMPLETE_TAG:
                    showToast(getString(R.string.tag_list_download_complete),
                            Toast.LENGTH_SHORT);
                    break;
                case MESSAGE_TIMEOUT_TAG:
                    showToast(
                            getString(R.string.tag_list_download_failed_http),
                            Toast.LENGTH_LONG);
                    break;
                case MESSAGE_NO_RESPONSE_TAG:
                    showToast(
                            getString(R.string.tag_list_download_failed_server),
                            Toast.LENGTH_LONG);
                    break;
                }
            }
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        setTitle(R.string.title_whatsinvasive);
        
        // Change the default exception handler so that we can collect debug information
        // on an uncaught exception
        final UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Log.e("WI UncaughtException", "Error: ", ex);
                DebugCollector debug = new DebugCollector(WhatsInvasive.this);
                try {
                    debug.collectAndCompress(new File(Environment
                            .getExternalStorageDirectory(), "whatsinvasive.zip")
                            .getPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                defaultHandler.uncaughtException(thread, ex);
            }
        });

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mConn = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder binder) {
                ITagDatabase service = ITagDatabase.Stub.asInterface(binder);

                try {
                    service.registerCallback(tagDatabaseCallback);
                    updatePark();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            public void onServiceDisconnected(ComponentName name) {
            }
        };

        Intent service = new Intent(this, LocationService.class);
        bindService(service, mConn, BIND_AUTO_CREATE);

        new Thread(new Runnable() {
            public void run() {
                checkVersion();
            }
        }).start();

        // start the upload service to check if there is anything to upload
        if (mPreferences.getBoolean("upload_service_on", true)) {
            Intent uploadService = new Intent(this, UploadService.class);
            this.startService(uploadService);
        }

        // Login if needed
        if (mPreferences.getString("username", null) == null
                || mPreferences.getString("password", null) == null) {
            Intent intent = new Intent(this, Login.class);

            this.startActivityForResult(intent, ACTIVITY_LOGIN);
        }
    }

    private void checkVersion() {
        final String appUrl = getString(R.string.app_url);
        String results[] = new String[4];
        final long park_id;

        String description = getString(R.string.whatsinvasive_no_details_update);
        String version = getString(R.string.version);
        String version_date = getString(R.string.version_date);

        final int availableVersion = currentVersion(version, version_date,
                results);

        description = results[0];
        version = results[1];
        version_date = results[2];

        long parsedParkId;

        try {
            parsedParkId = Long.parseLong(results[3]);
        } catch (NumberFormatException e) {
            parsedParkId = -1;
        }

        park_id = parsedParkId;

        if (availableVersion != NO_UPDATE_AVAILABLE) {
            Log.d(TAG, "should update version");

            mAlertDialogBuilder = new AlertDialog.Builder(this);

            if (availableVersion == UPDATE_REQUIRED)
                mAlertDialogBuilder
                        .setTitle(getString(R.string.msg_update_new_version_required));
            else
                mAlertDialogBuilder
                        .setTitle(getString(R.string.msg_update_new_version_available));

            mAlertDialogBuilder
                    .setMessage(getString(R.string.msg_update_available)
                            + description + "\n");

            if (availableVersion == UPDATE_REQUIRED) {
                mAlertDialogBuilder.setNegativeButton(
                        getString(R.string.update_button_later),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dlg, int which) {
                                WhatsInvasive.this.finish();
                            }
                        });

                if (UNINSTALL_REQUIRED) {
                    mAlertDialogBuilder
                            .setMessage(getString(R.string.msg_update_please_uninstall)
                                    + description + "\n");
                    mAlertDialogBuilder.setPositiveButton(
                            getString(R.string.msg_update_uninstall),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dlg,
                                        int which) {
                                    Uri packageURI = Uri
                                            .parse("package:edu.ucla.cens.whatsinvasive");
                                    Intent uninstallIntent = new Intent(
                                            Intent.ACTION_DELETE, packageURI);
                                    startActivity(uninstallIntent);
                                    WhatsInvasive.this.finish();
                                }
                            });
                } else {
                    mAlertDialogBuilder.setPositiveButton(
                            getString(R.string.update_button_download),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dlg,
                                        int which) {
                                    startActivity(new Intent(
                                            Intent.ACTION_VIEW, Uri
                                                    .parse(appUrl)));
                                    WhatsInvasive.this.finish();
                                }
                            });
                }
            } else if (park_id != -1) { // if area_id was specified in the
                                        // update message
                mAlertDialogBuilder.setNegativeButton(
                        getString(R.string.update_button_later), null);
                mAlertDialogBuilder.setPositiveButton(
                        getString(R.string.update_button_download_list),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dlg, int sumthin) {
                                // TODO: Update this to use a blocking dialog
                                showToast(
                                        getString(R.string.attempting_download_new_invasive),
                                        Toast.LENGTH_SHORT);

                                TagUpdateThread thread = new TagUpdateThread(
                                        WhatsInvasive.this, park_id);
                                thread.getObservable().addObserver(
                                        WhatsInvasive.this);
                                thread.start();
                            }
                        });
            } else { // if area_id was not specified in the update message just
                     // update the app
                mAlertDialogBuilder.setNegativeButton(
                        getString(R.string.update_button_later), null);
                mAlertDialogBuilder.setPositiveButton(
                        getString(R.string.update_button_download),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dlg, int sumthin) {
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse(appUrl)));
                                WhatsInvasive.this.finish();
                            }
                        });
            }

            handler.post(new Runnable() {
                public void run() {
                    mAlertDialogBuilder.show();
                }
            });
        }
    }

    private int currentVersion(String version, String date, String[] description) {
        HttpClient httpClient = new CustomHttpClient();
        HttpGet request = new HttpGet(getString(R.string.version_url));
        description[0] = new String("");
        description[1] = new String("");
        description[2] = new String("");
        description[3] = new String("");
        try {
            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                HttpEntity entity = response.getEntity();
                InputStream is = entity.getContent();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();

                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
                int intdate = Integer.parseInt(date);

                try {
                    JSONObject json = new JSONObject(new JSONTokener(
                            sb.toString()));
                    int jsondate = Integer.parseInt(json.getString("date"));
                    if (!version.equals(json.getString("version"))
                            && (intdate < jsondate)) {
                        UNINSTALL_REQUIRED = json.getString("uninstall")
                                .equals("1");
                        if (!json.isNull("description")) {
                            description[0] = new String(
                                    json.getString("description"));
                        } else {
                            description[0] = getString(R.string.whatsinvasive_no_details_update);
                        }
                        description[1] = new String(json.getString("version"));
                        description[2] = new String(json.getString("date"));
                        if (!json.isNull("area_id")) {
                            description[3] = new String(
                                    json.getString("area_id"));
                        }
                        if (json.getString("required").equals("1"))
                            return UPDATE_REQUIRED;
                        else
                            return UPDATE_AVAILABLE;
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return NO_UPDATE_AVAILABLE;
    }

    protected void updatePark() {
        TagDatabase db = new TagDatabase(this);
        db.openRead();

        long parkId = LocationService.getParkId(this);
        int availablePlantTags = db.getTagsAvailable(parkId, TagType.WEED);
        int availableAnimalTags = db.getTagsAvailable(parkId, TagType.BUG);

        db.close();

        Button captureWeed = (Button) this.findViewById(R.id.ButtonTagWeed);
        Button captureBug = (Button) this.findViewById(R.id.ButtonTagPest);

        captureWeed.setEnabled(availablePlantTags > 0);
        captureWeed
                .setText((availablePlantTags > 0) ? R.string.main_button_mapweed
                        : R.string.main_button_noweeds);
        captureWeed.setTextSize((availablePlantTags > 0) ? 20 : 18);
        captureBug.setEnabled(availableAnimalTags > 0);
        captureBug
                .setText((availableAnimalTags > 0) ? R.string.main_button_mappest
                        : R.string.main_button_nopests);
        captureBug.setTextSize((availableAnimalTags > 0) ? 20 : 18);

        // Set park caption
        TextView location = (TextView) this.findViewById(R.id.Location);
        TextView locationServiceStatus = (TextView) this.findViewById(R.id.LocationServiceStatus);

        String title, title2;
        if (mPreferences.getBoolean("location_service_on", true)) {
            title2 = getString(R.string.location_setting_auto);
        } else {
            title2 = getString(R.string.location_setting_manual);
        }
        title = LocationService.getParkTitle(this);

        if (title != null)
            location.setText(title);
        else
            location.setText(getString(R.string.no_park_selected));
        locationServiceStatus.setText(title2);
    }

    protected void setUserName() {
        // Display user name at bottom of screen
        TextView textView = (TextView) this.findViewById(R.id.Username);

        String username = mPreferences.getString("username", "");
        if (!username.equals("")) {
            textView.setText(username);
        } else
            textView.setText(getString(R.string.whatsinvasive_notlogged));
    }
    
    @Override
    protected void onResume() {
        updatePark();
        setUserName();

        super.onResume();
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "close service connection");
        mPreferences.edit().putBoolean("gps_off_alert", true).commit();

        this.unbindService(mConn);

        if (mPreferences.getBoolean("debug_mode", false)) {
            DebugCollector debug = new DebugCollector(this);
            try {
                debug.collectAndCompress(new File(Environment
                        .getExternalStorageDirectory(), "whatsinvasive.zip")
                        .getPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        super.onDestroy();
    }

    protected void showToast(String text, int length) {
        Toast.makeText(this, text, length).show();
    }
    
    public void onLogoClick(View v) {
        Intent intent = new Intent(WhatsInvasive.this, Splash.class);
        intent.putExtra("return", true);
        WhatsInvasive.this.startActivity(intent);
    }
    
    public void onTagWeedClick(View v) {
        mPreferences.edit().putBoolean("gps_off_alert2", true).commit();
        Intent intent = new Intent(WhatsInvasive.this,
                TagLocation.class);
        intent.putExtra("Type", TagType.WEED);
        WhatsInvasive.this.startActivity(intent);
    }
    
    public void onTagPestClick(View v) {
        mPreferences.edit().putBoolean("gps_off_alert2", true).commit();
        Intent intent = new Intent(WhatsInvasive.this,
                TagLocation.class);
        intent.putExtra("Type", TagType.BUG);
        WhatsInvasive.this.startActivity(intent);
    }
    
    public void onMapClick(View v) {
        Toast.makeText(this, "Coming Soon!", Toast.LENGTH_SHORT).show();
    }
    
    public void onResultsClick(View v) {
        Intent intent = new Intent(WhatsInvasive.this, FeedbackTabs.class);
        WhatsInvasive.this.startActivity(intent);
    }
    
    public void onNewsClick(View v) {
        Toast.makeText(this, "Coming Soon!", Toast.LENGTH_SHORT).show();
    }
    
    public void onWeedOfTheWeekClick(View v) {
        Toast.makeText(this, "Coming Soon!", Toast.LENGTH_SHORT).show();
    }
    
    public void onLocationClick(View v) {
        Intent intent = new Intent(WhatsInvasive.this, AreaList.class);
        WhatsInvasive.this.startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ABOUT, 0, getString(R.string.menu_about)).setIcon(
                android.R.drawable.ic_menu_info_details);
        menu.add(0, MENU_QUEUE, 2, getString(R.string.menu_queue)).setIcon(
                android.R.drawable.ic_menu_sort_by_size);
        menu.add(0, MENU_HELP, 1, getString(R.string.menu_main_help)).setIcon(
                android.R.drawable.ic_menu_help);
        menu.add(0, MENU_SETTINGS, 3, getString(R.string.menu_settings))
                .setIcon(android.R.drawable.ic_menu_preferences);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = null;

        switch (item.getItemId()) {
        case MENU_ABOUT:
            intent = new Intent(WhatsInvasive.this, Welcomer.class);
            WhatsInvasive.this.startActivityForResult(intent, WELCOMER_RESULT);
            break;
        case MENU_QUEUE:
            intent = new Intent(WhatsInvasive.this, Queue.class);
            WhatsInvasive.this.startActivity(intent);
            break;
        case MENU_SETTINGS:
            intent = new Intent(WhatsInvasive.this, Settings.class);
            WhatsInvasive.this.startActivity(intent);
            break;
        case MENU_HELP:
            intent = new Intent(this, HelpImg.class);
            intent.putExtra("help type", HelpImg.MAIN_HELP);
            startActivity(intent);
            break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
        case ACTIVITY_LOGIN:
            if (resultCode == Activity.RESULT_OK) {
                checkVersion();

                mPreferences.edit().putBoolean("first_run", false).commit();

                OnClickListener listener2 = new OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        Intent intent = new Intent(WhatsInvasive.this,
                                HelpImg.class);

                        switch (i) {
                        case AlertDialog.BUTTON1:
                            mPreferences.edit()
                                    .putBoolean("upload_service_on", true)
                                    .commit();
                            break;
                        case AlertDialog.BUTTON2:
                            mPreferences.edit()
                                    .putBoolean("upload_service_on", false)
                                    .commit();
                            break;
                        }

                        WhatsInvasive.this.startActivityForResult(intent,
                                HELP_IMAGE);
                    }
                };

                AlertDialog dialog = new AlertDialog.Builder(this).create();
                dialog.setMessage(getString(R.string.auto_upload_query));
                dialog.setButton(getString(R.string.sure), listener2);
                dialog.setButton2(getString(R.string.no_thanks), listener2);
                dialog.show();
            } else {
                this.finish();
            }

            break;
        case WELCOMER_RESULT:
            // do nothing for now
            break;
        case CHANGE_GPS_SETTINGS:
            Message msg = new Message();
            msg.arg1 = 5;
            this.handler.sendMessage(msg);
            break;
        case CHANGE_GPS_SETTINGS_2:
            WhatsInvasive.this.showDialog(WhatsInvasive.BLOCKING_TAG);
            Intent intent = new Intent(WhatsInvasive.this,
                    BlockingTagUpdate.class);
            this.startActivityForResult(intent, CHANGE_GPS_SETTINGS);
            break;
        case HELP_IMAGE:
            if (resultCode == RESULT_OK) {
                TagLocation.isLocationEnabled(WhatsInvasive.this);
            }

            break;
        }
    }

    public void update(Observable observable, Object data) {
        UpdateData update = (UpdateData) data;
        if (update.source.equals("TagUpdateThread")) {
            if (update.allDone) {
                this.handler.sendEmptyMessage(MESSAGE_COMPLETE_TAG);
            } else if (update.description.equals("timeout")) {
                this.handler.sendEmptyMessage(MESSAGE_TIMEOUT_TAG);
            } else if (update.description.equals("no_response")) {
                this.handler.sendEmptyMessage(MESSAGE_NO_RESPONSE_TAG);
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        ProgressDialog progress;

        switch (id) {

        case BLOCKING_TAG:
            progress = new ProgressDialog(WhatsInvasive.this);
            progress.setTitle(getString(R.string.updating_invasives));
            progress.setMessage(getString(R.string.please_wait_downloading));
            progress.setIndeterminate(true);
            progress.setCancelable(false);

            dialog = progress;
            break;

        }

        return dialog;
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == event.KEYCODE_BACK) {
            boolean flag = false;
            if(event.getRepeatCount() == 3) {
                finish();
                return true;
            }
            else if(event.getRepeatCount() == 0 && flag == false){
                showToast(getString(R.string.exit_back_alert), Toast.LENGTH_SHORT);
                flag = true;
            }
        }
            
        return false;
    }
}