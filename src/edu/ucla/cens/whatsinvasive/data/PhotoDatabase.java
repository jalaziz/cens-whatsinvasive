package edu.ucla.cens.whatsinvasive.data;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class PhotoDatabase {
	
	public static final String KEY_PHOTO_LONGITUDE = "photo_longitude";
	public static final String KEY_PHOTO_LATITUDE = "photo_latitude";
	public static final String KEY_PHOTO_TIME = "photo_time";
	public static final String KEY_PHOTO_FILENAME = "photo_filename";
	public static final String KEY_PHOTO_TAGS = "photo_tags";
	public static final String KEY_PHOTO_UPLOADED = "photo_uploaded";
	public static final String KEY_PHOTO_AREA = "photo_area";
	public static final String KEY_PHOTO_AMOUNT = "photo_amount";
	public static final String KEY_PHOTO_ROWID = "_id";
	public static final String KEY_PHOTO_NOTE = "photo_note";
	
	private static boolean databaseOpen = false;
	private static Object dbLock = new Object();
	public static final String TAG = "photoDB";
	private final DatabaseHelper dbHelper;
	private SQLiteDatabase db;
	 
	private Context mCtx = null;
	
	private static final String DATABASE_NAME = "photo_db";
	private static final String DATABASE_TABLE = "photo_table";
	private static final int DATABASE_VERSION = 2;
	private static final int UPLOAD_DELAY_SEC = 60; // seconds
	
	private static final String DATABASE_CREATE = "create table photo_table (_id integer primary key autoincrement, "
		+ KEY_PHOTO_LONGITUDE +" text,"
		+ KEY_PHOTO_LATITUDE +" text,"
		+ KEY_PHOTO_TIME +" text not null,"
		+ KEY_PHOTO_FILENAME +" text,"
		+ KEY_PHOTO_UPLOADED +" text,"
		+ KEY_PHOTO_AREA +" integer,"
		+ KEY_PHOTO_TAGS +" text not null,"
		+ KEY_PHOTO_AMOUNT +" text not null,"
		+ KEY_PHOTO_NOTE + " text"
		+ ");";
	
    public class PhotoDatabaseRow {
    	public long rowValue;
    	public long areaValue;
    	public String lonValue;
    	public String latValue;
    	public String timeValue;
    	public String filenameValue;            	
    	public String tagsValue;
    	public String uploadValue;
    	public String amountValue;
    	public String noteValue;
    }
	
	private static class DatabaseHelper extends SQLiteOpenHelper
	{
		DatabaseHelper(Context ctx)
		{
			super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

			db.execSQL("DROP TABLE EXISTS " + DATABASE_TABLE);
			onCreate(db);
		}		
	}
	
	public PhotoDatabase(Context ctx)
	{
		mCtx = ctx;
		dbHelper = new DatabaseHelper(mCtx);
	}
	
	public PhotoDatabase open() throws SQLException{

		synchronized(dbLock)
		{
			while (databaseOpen)
			{
				try
				{
					dbLock.wait();
				}
				catch (InterruptedException e){}

			}
			
			databaseOpen = true;
			db = dbHelper.getWritableDatabase();

			return this;
		}	
	}
	
	public void close()
	{
		synchronized(dbLock)
		{
			dbHelper.close();
			databaseOpen = false;
			dbLock.notify();
		}
	}
	
	public long createPhoto(String lon, String lat, String time, String filename, String tags, Long area, String amount, String note)
	{		
		ContentValues vals = new ContentValues();
		vals.put(KEY_PHOTO_LONGITUDE, lon);
		vals.put(KEY_PHOTO_LATITUDE, lat);
		vals.put(KEY_PHOTO_TIME, time);
		vals.put(KEY_PHOTO_FILENAME, filename);
		vals.put(KEY_PHOTO_TAGS, tags);
		vals.put(KEY_PHOTO_AREA, area);
		vals.put(KEY_PHOTO_AMOUNT, amount);
		vals.put(KEY_PHOTO_NOTE, note);
		
		long rowid = db.insert(DATABASE_TABLE, null, vals);
		return rowid;
	}
	
	public boolean deletePhoto(long rowId)
	{
		int count = 0;
		count = db.delete(DATABASE_TABLE, KEY_PHOTO_ROWID+"="+rowId, null);
		
		if(count > 0)
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	public Cursor getReadCursor(){
	    synchronized(dbLock) {
	        if(!databaseOpen) {
	            db = dbHelper.getReadableDatabase();
	        }
	    }
	    
		return db.query(DATABASE_TABLE, new String[]{KEY_PHOTO_ROWID, KEY_PHOTO_FILENAME, KEY_PHOTO_TAGS, KEY_PHOTO_TIME, KEY_PHOTO_LATITUDE, KEY_PHOTO_LONGITUDE, KEY_PHOTO_AREA, KEY_PHOTO_UPLOADED, KEY_PHOTO_AMOUNT, KEY_PHOTO_NOTE}, null, null, null, null, KEY_PHOTO_TIME + " DESC");
	}
	
	public ArrayList<PhotoDatabaseRow> fetchUploadedPhotos(int max) {
		ArrayList<PhotoDatabaseRow> ret = new ArrayList<PhotoDatabaseRow>();
		
		try
		{
			String where = "NOT "+ KEY_PHOTO_UPLOADED +" IS NULL";
			
			Cursor c = db.query(DATABASE_TABLE, new String[] {KEY_PHOTO_ROWID, KEY_PHOTO_LONGITUDE, KEY_PHOTO_LATITUDE, KEY_PHOTO_TIME, KEY_PHOTO_FILENAME, KEY_PHOTO_TAGS, KEY_PHOTO_AREA, KEY_PHOTO_UPLOADED, KEY_PHOTO_AMOUNT, KEY_PHOTO_NOTE}, where, null, null, null, null);
			int read = 0;
			
			while(c.moveToNext() && read < max)
			{
				read++;
				
				PhotoDatabaseRow pr = new PhotoDatabaseRow();
				
				pr.rowValue = c.getLong(0);
				pr.lonValue = c.getString(1);
				pr.latValue = c.getString(2);
				pr.timeValue = c.getString(3);
				pr.filenameValue = c.getString(4);
				pr.tagsValue = c.getString(5);
				pr.areaValue = c.getLong(6);
				pr.uploadValue = c.getString(7);
				pr.amountValue = c.getString(8);
				pr.noteValue = c.getString(9);
				
				ret.add(pr);
			}
			
			c.close();
		}
		catch (Exception e){
			Log.e(TAG, e.getMessage());
		}
		
		return ret;
	}
	
	public ArrayList<PhotoDatabaseRow> fetchPendingPhotos() {
		 return fetchPendingPhotos(UPLOAD_DELAY_SEC);
	}
	
	public ArrayList<PhotoDatabaseRow> fetchPendingPhotos(int delay) {
		ArrayList<PhotoDatabaseRow> ret = new ArrayList<PhotoDatabaseRow>();
		
		try
		{
			String where = KEY_PHOTO_UPLOADED +" IS NULL AND (strftime('%s', 'now')-strftime('%s', "+ KEY_PHOTO_TIME +"))>"+ delay;
			
			Cursor c = db.query(DATABASE_TABLE, new String[] {KEY_PHOTO_ROWID, KEY_PHOTO_LONGITUDE, KEY_PHOTO_LATITUDE, KEY_PHOTO_TIME, KEY_PHOTO_FILENAME, KEY_PHOTO_TAGS, KEY_PHOTO_AREA, KEY_PHOTO_UPLOADED, KEY_PHOTO_AMOUNT, KEY_PHOTO_NOTE}, where, null, null, null, null);
			int numRows = c.getCount();
			
			c.moveToFirst();
			
			for (int i =0; i < numRows; ++i)
			{
				PhotoDatabaseRow pr = new PhotoDatabaseRow();
				
				pr.rowValue = c.getLong(0);
				pr.lonValue = c.getString(1);
				pr.latValue = c.getString(2);
				pr.timeValue = c.getString(3);
				pr.filenameValue = c.getString(4);
				pr.tagsValue = c.getString(5);
				pr.areaValue = c.getLong(6);
				pr.uploadValue = c.getString(7);
				pr.amountValue = c.getString(8);
				pr.noteValue = c.getString(9);
				
				ret.add(pr);
				
				c.moveToNext();
				
			}
			c.close();			
		}
		catch (Exception e){
			Log.e(TAG, e.getMessage());
		}
		return ret;
	}
	
	public PhotoDatabaseRow fetchPhoto(long rowId) throws SQLException
	{
		Cursor c = db.query(DATABASE_TABLE, new String[] {KEY_PHOTO_ROWID, KEY_PHOTO_LONGITUDE, KEY_PHOTO_LATITUDE, KEY_PHOTO_TIME, KEY_PHOTO_FILENAME, KEY_PHOTO_TAGS, KEY_PHOTO_AREA, KEY_PHOTO_UPLOADED, KEY_PHOTO_AMOUNT, KEY_PHOTO_NOTE}, KEY_PHOTO_ROWID+"="+rowId, null, null, null, null);
		PhotoDatabaseRow ret = new PhotoDatabaseRow();

		if (c != null) {
			c.moveToFirst();
						
			ret.rowValue = c.getLong(0);
			ret.lonValue = c.getString(1);
			ret.latValue = c.getString(2);
			ret.timeValue = c.getString(3);
			ret.filenameValue = c.getString(4);
			ret.tagsValue = c.getString(5);
			ret.areaValue = c.getLong(6);
			ret.uploadValue = c.getString(7);
			ret.amountValue = c.getString(8);
			ret.noteValue = c.getString(9);
		}
		else
		{
			ret.rowValue = ret.areaValue = -1;
			ret.lonValue = ret.latValue = ret.timeValue = ret.filenameValue = ret.tagsValue = ret.amountValue = ret.noteValue  = null;
		}
		c.close();
		return ret;
	}
	
	public void updatePhotoUploaded(long rowId) {
		db.execSQL("UPDATE "+ DATABASE_TABLE +" SET "+ KEY_PHOTO_UPLOADED +"=datetime('now') WHERE "+ KEY_PHOTO_ROWID +"="+ rowId);
	}
	
	public boolean updatePhoto(long rowId, String lon, String lat, String time, String filename, String tags, Long area, String amount, String note) {
		ContentValues vals = new ContentValues();
		vals.put(KEY_PHOTO_LONGITUDE, lon);
		vals.put(KEY_PHOTO_LATITUDE, lat);
		vals.put(KEY_PHOTO_TIME, time);
		vals.put(KEY_PHOTO_FILENAME, filename);
		vals.put(KEY_PHOTO_TAGS, tags);
		vals.put(KEY_PHOTO_AREA, area);
		vals.put(KEY_PHOTO_AMOUNT, amount);
		vals.put(KEY_PHOTO_NOTE, note);
		
		return db.update(DATABASE_TABLE, vals,KEY_PHOTO_ROWID+"="+rowId, null) > 0;
	}
	
	public boolean updateNote(long rowId, String note) {
        ContentValues vals = new ContentValues();
        vals.put(KEY_PHOTO_NOTE, note);
        
        return db.update(DATABASE_TABLE, vals,KEY_PHOTO_ROWID+"="+rowId, null) > 0;
    }
}
