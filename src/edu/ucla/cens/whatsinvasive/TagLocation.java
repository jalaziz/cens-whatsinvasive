package edu.ucla.cens.whatsinvasive;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import edu.ucla.cens.whatsinvasive.data.PhotoDatabase;
import edu.ucla.cens.whatsinvasive.data.TagDatabase;
import edu.ucla.cens.whatsinvasive.services.LocationService;
import edu.ucla.cens.whatsinvasive.services.UploadService;
import edu.ucla.cens.whatsinvasive.tools.Media;

public class TagLocation extends ListActivity implements LocationListener {
    private final int CONTEXT_HELP = 0;

    public static final String PREFERENCES_USER = "user";
    
    private TagDatabase mDatabase;
    
    private TagType tagType;

    private static final int ACTIVITY_CAPTURE_PHOTO = 0;
    
    private static final int ACTIVITY_CAPTURE_NOTE = 1;

    private static final int MENU_HELP = 0;

    protected static final String TAG = "Tag Location";

    private static final int QUEUE_SCREEN = 323;

    private static final int SETTINGS_HELP = 182;

    private static final int ACTIVITY_SETTINGS_COMPLETE = 2318;

    private static final int GPS_POLL_INTERVAL = 20000;
    
    private static final int MAX_IMAGE_WIDTH = 1280;
    
    private static final int MAX_IMAGE_HEIGHT = 960;

    // private Location current_location;

    private CheckBox mPhoto;
    private CheckBox mNote;

    private RadioGroup mRadioGroup;

    // Check user preferences
    SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkGPS();

        // Check user preferences
        mPreferences = this.getSharedPreferences(PREFERENCES_USER,
                Activity.MODE_PRIVATE);

        tagType = (TagType)TagLocation.this.getIntent().getSerializableExtra("Type");
        
        if(tagType == null) tagType = TagType.WEED;

        // show tag help on first run
        if (!mPreferences.getBoolean("Seen Tag Help", false)) {
            Intent help = new Intent(this, HelpImg.class);
            help.putExtra("help type", HelpImg.TAG_HELP);
            startActivity(help);
            mPreferences.edit().putBoolean("Seen Tag Help", true).commit();
        }

        // requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setTitle(R.string.title_taglocation);
        setContentView(R.layout.tag_location);
        
        mRadioGroup = (RadioGroup) findViewById(R.id.radio_group);
        mPhoto = (CheckBox) findViewById(R.id.with_photo);
        mNote = (CheckBox) findViewById(R.id.with_note);

        LocationManager lManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!lManager.isProviderEnabled("gps")) {
            setTitle(R.string.title_taglocation_no_gps);
            if (!lManager.isProviderEnabled("network")) {
                setTitle(R.string.title_taglocation_all_disabled);
            }
        }

        if (lManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    GPS_POLL_INTERVAL, 0, this);
        }

        mDatabase = new TagDatabase(this);
        mDatabase.openRead();
        
        Cursor tags = mDatabase.getTags(LocationService.getParkId(this), TagLocation.this.tagType);
        startManagingCursor(tags);
        
        getListView().addHeaderView(getListHeader());

        setListAdapter(new TagAdapter(tags));
        
        getListView().setOnItemClickListener(new TagItemClickListener());
        
        updateButtons();
        
        createPhotoDirectory();
    }

    private View getListHeader() {
        final float scale = getResources().getDisplayMetrics().density;
        
        LinearLayout layout = new LinearLayout(this);   
        layout.setLayoutParams(new ListView.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
        layout.setBackgroundColor(getResources().getColor(R.color.dark_gray));
        layout.setClickable(false);
        layout.setLongClickable(false);
        
        TextView info = new TextView(this);
        info.setGravity(Gravity.CENTER);
        info.setWidth((int) ((105 * scale) + 0.5f));
        info.setText(R.string.header_tag_info);
        info.setTextSize(16);
        info.setTextColor(getResources().getColor(R.color.white));
        
        View divider = new View(this);
        divider.setBackgroundResource(R.drawable.divider_vertical_light);
        divider.setLayoutParams(new LinearLayout.LayoutParams(1, LayoutParams.FILL_PARENT));
        
        TextView observation = new TextView(this);
        observation.setGravity(Gravity.CENTER);
        observation.setText(R.string.header_tag_observation);
        observation.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1));
        observation.setTextSize(16);
        observation.setTextColor(getResources().getColor(R.color.white));
        
        layout.addView(info);
        layout.addView(divider);
        layout.addView(observation);
        
        return layout;
    }

    @Override
    public void onDestroy() {
        LocationManager lManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lManager.removeUpdates(this);
        }
        
        mDatabase.close();
        
        super.onDestroy();
    }

    private void updateButtons() {
        RadioButton radio_amount = (RadioButton) findViewById(mPreferences
                .getInt("amountId", mRadioGroup.getCheckedRadioButtonId()));
        radio_amount.toggle();

        mRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(RadioGroup group, int checkedId) {
                mPreferences.edit().putInt("amountId", checkedId).commit();
            }

        });

        mPhoto.setChecked(mPreferences.getBoolean("photo", true));
        mPhoto.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mPreferences.edit().putBoolean("photo", mPhoto.isChecked())
                        .commit();

            }
        });
        
        mNote.setChecked(mPreferences.getBoolean("note", true));
        mNote.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mPreferences.edit().putBoolean("note", mNote.isChecked())
                        .commit();

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case ACTIVITY_CAPTURE_PHOTO:
            if (resultCode == Activity.RESULT_OK) {
                
                final String fileName = this.getIntent().getStringExtra("filename");

                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    
                    options.inJustDecodeBounds= true;
                    BitmapFactory.decodeFile(fileName, options);
                    
                    int factor = Math.max(options.outWidth / MAX_IMAGE_WIDTH, 
                            options.outHeight / MAX_IMAGE_HEIGHT);
                    
                    options.inSampleSize = msb32(factor);                    
                    options.inPurgeable = true;
                    options.inInputShareable = true;
                    options.inJustDecodeBounds= false;
                    Bitmap image = BitmapFactory.decodeFile(fileName, options);
                    Bitmap scaledImage;
                    
                    if(image.getWidth() > MAX_IMAGE_WIDTH || image.getHeight() > MAX_IMAGE_HEIGHT) {
                        float scale = Math.min(MAX_IMAGE_WIDTH/(float)image.getWidth(), 
                                MAX_IMAGE_HEIGHT/(float)image.getHeight());
                        int newWidth = (int) ((image.getWidth() * scale) + 0.5f);
                        int newHeight = (int) ((image.getHeight() * scale) + 0.5f);
                        scaledImage = Bitmap.createScaledBitmap(image, newWidth, newHeight, true);
                        image.recycle();
                    } else {
                        scaledImage = image;
                    }
                    
                    OutputStream os = new FileOutputStream(fileName);
                    scaledImage.compress(CompressFormat.JPEG, 80, os);
                    os.close();
                } catch (FileNotFoundException e) {
                } catch (IOException e) {
                } catch (OutOfMemoryError e) {
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.out_of_memory_title)
                        .setMessage(R.string.out_of_memory_photo_msg)
                        .setPositiveButton(R.string.out_of_memory_photo_retake, new OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                new File(fileName).delete();
                                capturePhoto();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                    
                    return;
                }

                captureNote();
            }

            break;
        case ACTIVITY_CAPTURE_NOTE:
            if(resultCode == Activity.RESULT_OK) {
                String note = data.getExtras().getString("note");
                this.getIntent().putExtra("note", note);
                recordObservation();
            }
            break;
        case ACTIVITY_SETTINGS_COMPLETE:
            updateButtons();
            break;
        }
    }
    
    private int msb32(int x)
    {
        x |= (x >> 1);
        x |= (x >> 2);
        x |= (x >> 4);
        x |= (x >> 8);
        x |= (x >> 16);
        return (x & ~(x >> 1));
    }

    private void checkGPS() {
        LocationManager manager = (LocationManager) TagLocation.this
                .getSystemService(Context.LOCATION_SERVICE);

        SharedPreferences preferences = TagLocation.this.getSharedPreferences(
                WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
        boolean gpsOffAlert = preferences.getBoolean("gpsOffAlert", true);
        boolean gpsOffAlert2 = preferences.getBoolean("gpsOffAlert2", true);

        if (gpsOffAlert
                && gpsOffAlert2
                && !(manager.isProviderEnabled("gps") || manager
                        .isProviderEnabled("network"))) {
            AlertDialog alert = new AlertDialog.Builder(TagLocation.this)
                    .setTitle(getString(R.string.msg_gps_unavailable))
                    .setMessage(getString(R.string.msg_gps_unavailable_long))
                    .setPositiveButton(android.R.string.ok,
                            new OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    SharedPreferences preferences = TagLocation.this
                                            .getSharedPreferences(
                                                    WhatsInvasive.PREFERENCES_USER,
                                                    Activity.MODE_PRIVATE);
                                    preferences.edit().putBoolean(
                                            "gpsOffAlert2", false).commit();
                                }
                            }).setNegativeButton(R.string.dontremind,
                            new OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    SharedPreferences preferences = TagLocation.this
                                            .getSharedPreferences(
                                                    WhatsInvasive.PREFERENCES_USER,
                                                    Activity.MODE_PRIVATE);
                                    preferences.edit().putBoolean(
                                            "gpsOffAlert", false).commit();
                                }
                            }).create();

            alert.show();
        }
    }

    private boolean saveToDatabase(String tag, String amount, String note,
            String filename) {

        PhotoDatabase pdb = new PhotoDatabase(this);

        String longitude = null;
        String latitude = null;

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        /* This is the new method of sending the date and time.
         * It uses the ISO 8601 standard which include time zone information.
         * TODO: Uncomment the following lines of code when the server-side code has been updated
        Calendar calendar = Calendar.getInstance();
        W3CDateFormat dateFormat = new W3CDateFormat(W3CDateFormat.Pattern.SECOND);
        */
        
        String time = dateFormat.format(calendar.getTime());

        // The logic here is this: if GPS is available and the lock happened
        // less than ten minutes ago, go with the GPS. Otherwise, try to use network
        // location.
        long gpsdelay = 60 * 10;

        LocationManager lManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String text = "Using GPS\n";
        Location loc = lManager.getLastKnownLocation("gps");
        long curtime = System.currentTimeMillis() / 1000;
        
        if (loc == null || ((curtime - (loc.getTime() / 1000)) > gpsdelay)) {
            loc = lManager.getLastKnownLocation("network");
            text = "Using network\n";
        }

        if (loc != null) {
            text += (curtime - (loc.getTime() / 1000)) + " seconds since last lock.\n";
            
            longitude = Double.toString(loc.getLongitude());
            latitude = Double.toString(loc.getLatitude());
        } else {
            longitude = latitude = "0";
        }
        
        Log.d("GPS INFO", text + "lat: " + latitude + "\nlng:" + longitude);
        if (tag.equals("Other\nPlant")) {
            tag = "Other";
        }

        if (tag.equals("notvalid") && (filename != null)) {
            File file = null;
            file = new File(filename.toString());

            if (file != null) {
                file.delete();
            }

            return false;
        } else {
            // Lets add it to a DB (the filename will be NULL if no picture was
            // taken)
            pdb.open();
            long photo_created = pdb.createPhoto(longitude, latitude, time,
                    filename, tag, LocationService.getParkId(this), amount, note);
            pdb.close();

            // start the upload service if auto upload is on because we now have
            // data to upload
            if (photo_created != -1
                    && mPreferences.getBoolean("uploadServiceOn", true)) {
                Intent service = new Intent(this, UploadService.class);
                this.startService(service);

                mPreferences.edit().putBoolean("uploadServiceOn", true).commit();
            }

            if (photo_created == -1)
                return false;
            else
                return true;
        }
    }

    public static boolean isLocationEnabled(final Activity activity) {
        LocationManager manager = (LocationManager) activity
                .getSystemService(Context.LOCATION_SERVICE);
        SharedPreferences preferences = activity.getSharedPreferences(
                WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
        preferences.edit().putLong("fixedArea", 9).commit();

        if (!(manager.isProviderEnabled("gps") || manager
                .isProviderEnabled("network"))) {
            AlertDialog alert = new AlertDialog.Builder(activity).setTitle(
                    activity.getString(R.string.msg_gps_location_disabled))
                    .setMessage(
                            activity.getString(R.string.msg_gps_cannot_access))
                    .setPositiveButton(android.R.string.ok,
                            new OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    Intent intent = new Intent(
                                            android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                    activity
                                            .startActivityForResult(
                                                    intent,
                                                    WhatsInvasive.CHANGE_GPS_SETTINGS_2);

                                }
                            }).setNegativeButton(android.R.string.cancel,
                            new OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    activity
                                            .showDialog(WhatsInvasive.BLOCKING_TAG);
                                    SharedPreferences preferences = activity
                                            .getSharedPreferences(
                                                    WhatsInvasive.PREFERENCES_USER,
                                                    Activity.MODE_PRIVATE);
                                    preferences.edit().putBoolean(
                                            "locationServiceOn", false)
                                            .commit();
                                    Intent intent = new Intent(activity, BlockingTagUpdate.class);
                                    activity.startActivityForResult(intent,
                                            WhatsInvasive.CHANGE_GPS_SETTINGS);

                                }
                            }).create();

            alert.show();

            return false;
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_HELP, 1, getString(R.string.help_tagging_title))
                .setIcon(android.R.drawable.ic_menu_help);
        menu.add(0, QUEUE_SCREEN, 2, getString(R.string.menu_queue)).setIcon(
                android.R.drawable.ic_menu_sort_by_size);
        menu.add(0, SETTINGS_HELP, 3, getString(R.string.menu_settings))
                .setIcon(android.R.drawable.ic_menu_preferences);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = null;

        switch (item.getItemId()) {
        case MENU_HELP:
            intent = new Intent(this, HelpImg.class);
            intent.putExtra("help type", HelpImg.TAG_HELP);
            startActivity(intent);
            break;
        case QUEUE_SCREEN:
            intent = new Intent(this, Queue.class);
            startActivity(intent);
            break;
        case SETTINGS_HELP:
            intent = new Intent(this, Settings.class);
            startActivityForResult(intent, ACTIVITY_SETTINGS_COMPLETE);
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onLocationChanged(Location location) {
        Time now = new Time();
        now.setToNow();

        if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
            setTitle(getString(R.string.title_taglocation_lastupdate_gps)
                    + now.format("%l:%M:%S%p") + " [�"
                    + (int) location.getAccuracy() + " m]");
        } else {
            setTitle(getString(R.string.title_taglocation_lastupdate_gps)
                    + now.format("%l:%M:%S%p")
                    + R.string.title_taglocation_network);
        }
    }

    public void onProviderDisabled(String provider) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            setTitle(R.string.title_taglocation_gpsdisabled);
            LocationManager lManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lManager.removeUpdates(this);
        }
    }

    public void onProviderEnabled(String provider) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            LocationManager lManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    GPS_POLL_INTERVAL, 0, this);
        }
    }

    public void onStatusChanged(String provider, int status, Bundle extras) { }
    
    private void capturePhoto() 
    {
        if (!mPhoto.isChecked()) {
            captureNote();
        } else {
            Date date = new Date();
            long time = date.getTime();
            String fileName = this.getString(R.string.pic_data_path) + "/"
                    + time + ".jpg";

            this.getIntent().putExtra("filename", fileName);
        
            Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(fileName)));
            startActivityForResult(intent, ACTIVITY_CAPTURE_PHOTO);
        }
    }
    
    private void captureNote() 
    {
        if(!mNote.isChecked()) {
            recordObservation();
        } else {
            
            Intent intent = new Intent(this, NoteEdit.class);
            String title = getString(R.string.note_title_prefix) + " " + getIntent().getStringExtra("Tag");
            intent.putExtra("title", title);
            startActivityForResult(intent, ACTIVITY_CAPTURE_NOTE);
        }
    }
    
    private void recordObservation()
    {
        Bundle extras = this.getIntent().getExtras();
        
        String tag = extras.getString("Tag");
        String amount = extras.getString("amount");
        String note = extras.getString("note");
        String filename = extras.getString("filename");
        
        Intent result = new Intent();
        result.putExtras(extras);
        
        // Erase the extras for the next observation
        this.getIntent().replaceExtras((Bundle)null);

        Log.d(TAG, "tag = " + tag + ", amount = " + amount);

        if (TagLocation.this.saveToDatabase(tag,
                amount, note, filename)) {
            Toast.makeText(this, getString(R.string.invasive_mapped_notice), 5).show();
        } else {
            Toast.makeText(this, getString(R.string.invasive_mapping_failed), Toast.LENGTH_LONG).show();
        }
    }
    
    private void createPhotoDirectory() {
        File ld = new File(this.getString(R.string.pic_data_path));
        if (ld.exists()) {
            if (!ld.isDirectory()) {
                // TODO Handle exception
            }
        } else {
            ld.mkdir();
        }
    }
    
    private class TagItemClickListener implements OnItemClickListener
    {
        public void onItemClick(AdapterView<?> arg0, View v, int arg2,
                long arg3) {
            Tag data = (Tag) v.getTag();
            
            if(data == null)
                return;

            final String tagtext = data.title;

            RadioButton radio_amount = (RadioButton) findViewById(mPreferences
                    .getInt("amountId", mRadioGroup.getCheckedRadioButtonId()));
            final String amounttext = radio_amount.getTag().toString();

            // Tag this invasive
            TagLocation.this.getIntent().putExtra("Type", tagType);
            TagLocation.this.getIntent().putExtra("Tag", tagtext);
            TagLocation.this.getIntent().putExtra("amount", amounttext);
            
            capturePhoto();
        }
    }

    private class Tag {
        public int id;

        public String title;
        
        public String scienceName;

        public String imagePath;

        public String flags;

        public String info;
    }
    
    private class TagAdapter extends ResourceCursorAdapter {

        private boolean mLoading;

        public TagAdapter(Cursor c) {
            super(TagLocation.this, R.layout.tag_list_item, c);
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }
        
        private Tag getTag(Cursor c) {
            Tag tag = new Tag();

            tag.id = c.getInt(c.getColumnIndex(TagDatabase.KEY_ID));
            tag.title = c.getString(c.getColumnIndex(TagDatabase.KEY_TITLE));
            tag.scienceName = c.getString(c.getColumnIndex(TagDatabase.KEY_SCIENCE_NAME));
            tag.info = c.getString(c.getColumnIndex(TagDatabase.KEY_TEXT));
            tag.imagePath = c.getString(c.getColumnIndex(TagDatabase.KEY_IMAGE_URL));
            tag.flags = c.getString(c.getColumnIndex(TagDatabase.KEY_FLAGS));

            return tag;
        }

        @Override
        public void bindView(View view, Context context, Cursor c) {
            
            Tag tag = getTag(c);

            TextView title = (TextView) view.findViewById(R.id.title);
            TextView scienceName = (TextView) view.findViewById(R.id.science_name);
            final ImageView image = (ImageView) view.findViewById(R.id.image);
            
            // set item title text

            title.setText(tag.title);
            scienceName.setText(tag.scienceName);

            if (tag.flags != null && tag.flags.contains("invasiveofweek")) {
                view.setBackgroundColor(Color.GREEN);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }

            if (tag.imagePath != null && new File(tag.imagePath).exists()) {
                Bitmap thumb = Media.resizeImage(tag.imagePath, new Media.Size(56, 56));

                //info.setPadding(thumb.getWidth() - 25, thumb.getHeight() - 40, 0, 0);

                image.setImageBitmap(thumb);
                image.setPadding(1, 1, 1, 1);
            } else {
                image.setImageResource(R.drawable.downloading);
                image.setPadding(1, 1, 1, 1);
            }
            
            View thumbnail = view.findViewById(R.id.Thumbnail);
            thumbnail.setTag(tag);
            thumbnail.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Tag data = (Tag) v.getTag();

                    Intent intent = new Intent(TagLocation.this,
                            TagHelp.class);
                    intent.putExtra("id", data.id);

                    TagLocation.this.startActivity(intent);
                }
            });

            view.setTag(tag);
        }
    }
}
