package edu.ucla.cens.whatsinvasive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;

public class Splash extends Activity {
    
    private static final String TAG = "Splash";
    
    private boolean m_firstRun = false;
    private boolean m_return;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.splash);

        SharedPreferences preferences = this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);

        m_firstRun = preferences.getBoolean("firstRun", true);		
        m_return = getIntent().getBooleanExtra("return", false);
        
        upgradeDirectory();
        createDataDirectory();

        new Handler().postDelayed(new Runnable() {
            public void run() {
                startNextActivity();
            }
        }, 2000);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            startNextActivity();
        }
        return true;
    }

    private synchronized void startNextActivity() {
        Intent intent;
        
        if(Splash.this.isFinishing())
            return;
        
        if(!m_return) {
            if(m_firstRun) {
                intent = new Intent(this, Welcomer.class);
            } else {
                intent = new Intent(this, WhatsInvasive.class); 
            }
            
            this.startActivity(intent);
        }
        
        Splash.this.finish();
    }
    
    private void upgradeDirectory() {
        File oldDir = new File("/sdcard/whatsinvasive/");
        File filesDir = new File(Environment.getExternalStorageDirectory(), getString(R.string.files_path));
        if(oldDir.exists()) {
            if(!filesDir.exists()) {
                try {
                    new File(getString(R.string.data_path),
                                ".nomedia").createNewFile();
                } catch (IOException e) {
                }
                if (!oldDir.renameTo(filesDir)) {
                    // Try the hard way
                    if(!MoveFiles.moveDirectory(oldDir, filesDir)) {
                        Log.w(TAG, "Unable to move old directory");
                    }
                }
            }
        }
    }
    
    private void createDataDirectory() {
        File filesDir = new File(Environment.getExternalStorageDirectory(), getString(R.string.files_path));
        if(!filesDir.exists()) {
            try {
                new File(getString(R.string.data_path), 
                            ".nomedia").createNewFile();
            } catch (IOException e) {
            }
            if(!filesDir.mkdirs()) {
                Log.w(TAG, "Unable to create files directory");
            }
        }
    }
    
    private static class MoveFiles {
        
        public static boolean moveDirectory(File source, File dest) {
            if(!copyDirectory(source, dest))
                return false;
            
            if(!delete(source))
                return false;
            
            return true;
        }
        
        
        public static boolean copyFile(File source, File dest) {
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
        
        public static boolean copyDirectory(File sourceDir, File destDir) {
            if(!destDir.exists()) {
                if(!destDir.mkdirs())
                    return false;
            }
            
            File[] children = sourceDir.listFiles();
            
            for(File sourceChild : children) {
                String name = sourceChild.getName();
                File destChild = new File(destDir, name);
                if(sourceChild.isDirectory()) {
                    if(!copyDirectory(sourceChild, destChild))
                        return false;
                }
                else {
                    if(!copyFile(sourceChild, destChild))
                        return false;
                }
            }
            
            return true;
        }
        
        public static boolean delete(File resource) { 
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
