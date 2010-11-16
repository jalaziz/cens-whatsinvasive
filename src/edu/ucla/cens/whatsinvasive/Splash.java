package edu.ucla.cens.whatsinvasive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;

public class Splash extends Activity {
    
    private static final String TAG = "Splash";
    
    private static final int DIALOG_MOVING_DATA = 0;
    
    private static final int SPLASH_TIME = 2000;
    
    private boolean mFirstRun = false;
    private boolean mReturn;
    private final Handler mHandler = new Handler();
    
    private UpgradeDataDirectoryTask mUpgradeTask;
    private ProgressDialog mUpgradeDialog;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.splash);
        
        // Set default preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        mFirstRun = preferences.getBoolean("first_run", true);		
        mReturn = getIntent().getBooleanExtra("return", false);
        
        mUpgradeTask = new UpgradeDataDirectoryTask();
        mUpgradeTask.execute((Void)null);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch(id) {
        case DIALOG_MOVING_DATA:
            mUpgradeDialog = new ProgressDialog(this);
            mUpgradeDialog.setMax(100);
            mUpgradeDialog.setTitle(R.string.dialog_upgrade_directory_title);
            mUpgradeDialog.setMessage(getString(R.string.dialog_upgrade_directory_message));
            mUpgradeDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mUpgradeDialog.setCancelable(false);
            
            return mUpgradeDialog;
        }
        
        return super.onCreateDialog(id);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN 
                && mUpgradeTask.getStatus() == AsyncTask.Status.FINISHED) {
            startNextActivity();
        }
        return true;
    }

    private synchronized void startNextActivity() {
        Intent intent;
        
        if(Splash.this.isFinishing())
            return;
        
        if(!mReturn) {
            if(mFirstRun) {
                intent = new Intent(this, Welcomer.class);
            } else {
                intent = new Intent(this, WhatsInvasive.class); 
            }
            
            this.startActivity(intent);
        }
        
        Splash.this.finish();
    }
    
    private class UpgradeDataDirectoryTask extends AsyncTask<Void, Integer, Void> {
        private long m_time;
        
        @Override
        protected void onPreExecute() {
            m_time = System.currentTimeMillis();
        }
        
        @Override
        protected Void doInBackground(Void... args) {
            if(new File("/sdcard/whatsinvasive").exists()) {
                publishProgress(-1);
                upgradeDirectory();
                publishProgress(-2);
            }
            
            createDataDirectory();
            
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            switch(progress[0]) {
            case -1:
                showDialog(DIALOG_MOVING_DATA);
                break;
            case -2:
                dismissDialog(DIALOG_MOVING_DATA);
                break;
            default:
                mUpgradeDialog.setProgress(progress[0]);
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            long delay = 0;
            
            m_time = System.currentTimeMillis() - m_time;
            
            if(m_time < SPLASH_TIME) {
                delay = SPLASH_TIME - m_time;
            }
            
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    startNextActivity();
                }
            }, delay);

        }
        
        private void upgradeDirectory() {
            Log.i(TAG, "Upgrading data directory location");
            File oldDir = new File("/sdcard/whatsinvasive/");
            File filesDir = new File(Environment.getExternalStorageDirectory(), getString(R.string.files_path));
            if(!filesDir.exists()) {
                try {
                    Log.i(TAG, "Creating files directory and .nomedia file at: " + filesDir.getPath());
                    new File(filesDir,
                                ".nomedia").createNewFile();
                } catch (IOException e) {
                }
                if (!oldDir.renameTo(filesDir)) {
                    // Try the hard way
                    if(!new MoveFiles().moveDirectory(oldDir, filesDir)) {
                        Log.w(TAG, "Unable to move old directory");
                    }
                }
            }
        }
        
        private void createDataDirectory() {
            File filesDir = new File(Environment.getExternalStorageDirectory(), getString(R.string.files_path));
            if(!filesDir.exists()) {
                try {
                    Log.i(TAG, "Creating files directory and .nomedia file at: " + filesDir.getPath());
                    new File(filesDir, 
                                ".nomedia").createNewFile();
                } catch (IOException e) { }
                
                if(!filesDir.mkdirs()) {
                    Log.w(TAG, "Unable to create files directory");
                }
            }
        }
        
        private class MoveFiles {
            
            public boolean moveDirectory(File source, File dest) {
                if(!copyDirectory(source, dest, true))
                    return false;
                
                if(!delete(source))
                    return false;
                
                return true;
            }
            
            
            public boolean copyFile(File source, File dest) {
                InputStream in = null;
                OutputStream out = null;
                
                try {
                    if(!dest.exists()) {
                        dest.createNewFile();
                    }
                
                    in = new FileInputStream(source);
                    out = new FileOutputStream(dest);
            
                    // Transfer bytes from in to out
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                } catch(Exception e) {
                    return false;
                } finally {
                    try {
                        if(in != null)
                            in.close();
                        if(out != null)
                            out.close();
                    } catch (IOException ex) { }
                }
                
                return true;
            }
            
            public boolean copyDirectory(File sourceDir, File destDir, boolean progress) {
                if(!destDir.exists()) {
                    if(!destDir.mkdirs())
                        return false;
                }
                
                File[] children = sourceDir.listFiles();
                
                int totalFiles = children.length;
                int i = 0;
                
                for(File sourceChild : children) {
                    String name = sourceChild.getName();
                    File destChild = new File(destDir, name);
                    if(sourceChild.isDirectory()) {
                        if(!copyDirectory(sourceChild, destChild, false))
                            return false;
                    }
                    else {
                        if(!copyFile(sourceChild, destChild))
                            return false;
                    }
                    
                    if(progress)
                        publishProgress((int)((++i / (float)totalFiles) * 100));
                }
                
                return true;
            }
            
            public boolean delete(File resource) { 
                if(resource.isDirectory()) {
                    File[] childFiles = resource.listFiles();
                    for(File child : childFiles) {
                        delete(child);
                    }
                                
                }
                return resource.delete();   
            }   
        }
    }
}
