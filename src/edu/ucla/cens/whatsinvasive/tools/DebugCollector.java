package edu.ucla.cens.whatsinvasive.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import org.focuser.sendmelogs.LogCollector;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import com.totsp.androidexamples.DataXmlExporter;

import edu.ucla.cens.whatsinvasive.R;

public class DebugCollector {
    private final Context mContext;
    private final String mDebugDirectory;
    private final String mPackageName;
    private final static String TAG = "DebugCollector";
    
    public DebugCollector(Context context) {
        mContext = context;
        mPackageName = context.getPackageName();
        mDebugDirectory = new File(Environment.getExternalStorageDirectory(), 
                context.getString(R.string.data_path)).getPath();
    }
    
    public void collect() {
        LogCollectorThread lct = new LogCollectorThread();
        lct.run();
        
        DatabaseBackupThread dbt = new DatabaseBackupThread();
        dbt.run();
        
        DatabaseExportThread det = new DatabaseExportThread();
        det.run();
        
        try {
            lct.join();
            dbt.join();
            det.join();
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
    
    public void compress(String filename) throws IOException {
        Compress.compress(mDebugDirectory, filename);
    }
    
    private class DatabaseExportThread extends Thread {
        @Override
        public void run() {
            File dbDir =
                new File(Environment.getDataDirectory() + "/data/" + mPackageName + "/databases/");

            File exportDir = new File(mDebugDirectory, "xml");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            
            for(String entry : dbDir.list()) {  
                try {
                    File db = new File(dbDir, entry);
                    DataXmlExporter dm = 
                        new DataXmlExporter(SQLiteDatabase.openDatabase(db.getPath(), null, 
                                SQLiteDatabase.OPEN_READONLY),
                                exportDir.getPath());
                    dm.export(entry, entry);
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }
    
    private class DatabaseBackupThread extends Thread {
        @Override
        public void run() {
            File dbDir =
                new File(Environment.getDataDirectory() + "/data/" + mPackageName + "/databases/");

            File exportDir = new File(mDebugDirectory, "databases");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            
            for(String entry : dbDir.list()) {
                File file = new File(exportDir, entry);
                
                try {
                    file.createNewFile();
                    this.copyFile(new File(dbDir, entry), file);
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
        
        private void copyFile(File src, File dst) throws IOException {
            FileChannel inChannel = new FileInputStream(src).getChannel();
            FileChannel outChannel = new FileOutputStream(dst).getChannel();
            try {
               inChannel.transferTo(0, inChannel.size(), outChannel);
            } finally {
               if (inChannel != null)
                  inChannel.close();
               if (outChannel != null)
                  outChannel.close();
            }
        } 
    }
    
    private class LogCollectorThread extends Thread {
        @Override
        public void run() {
            LogCollector logCollector = new LogCollector(mContext);
            if(logCollector.collect()) {
                try {
                    logCollector.writeLog(new File(mDebugDirectory, "log"));
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }
}
