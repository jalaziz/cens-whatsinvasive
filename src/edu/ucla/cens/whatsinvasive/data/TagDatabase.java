package edu.ucla.cens.whatsinvasive.data;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.Vector;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import edu.ucla.cens.whatsinvasive.TagType;

public class TagDatabase {
	public static final String KEY_ID = "_id";
	public static final String KEY_TITLE = "title";
	public static final String KEY_SCIENCE_NAME = "science_name";
	public static final String KEY_COMMON_NAMES = "common_names";
	public static final String KEY_IMAGE_URL = "imageurl";
	public static final String KEY_TEXT = "text";
	public static final String KEY_AREA_ID = "area";
	public static final String KEY_LATITUDE = "lat";
	public static final String KEY_LONGITUDE = "lon";
	public static final String KEY_UPDATED = "updated";
	public static final String KEY_DISTANCE = "distance";
	public static final String KEY_ORDER = "ordering";
	public static final String KEY_FLAGS = "flags";
	public static final String KEY_TYPE_ID = "type";
	
	private static final String DATABASE_NAME = "tag_db";
	private static final String DATABASE_TAGS_TABLE = "tags";
	private static final String DATABASE_AREAS_TABLE = "areas";
	
	private static final int DATABASE_VERSION = 3;
	
	private static boolean databaseOpen = false;
	private static Object dbLock = new Object();
	private final DatabaseHelper dbHelper;
	private SQLiteDatabase db;
	
	private Context context = null;
	
	private static final String DATABASE_TAGS_CREATE = "create table "+ DATABASE_TAGS_TABLE +" ("+ KEY_ID +" integer primary key autoincrement, "
		+ KEY_TITLE +" TEXT NOT NULL,"
		+ KEY_SCIENCE_NAME +" TEXT,"
		+ KEY_COMMON_NAMES +" TEXT,"
		+ KEY_IMAGE_URL +" TEXT,"
		+ KEY_TEXT +" TEXT,"
		+ KEY_AREA_ID +" INTEGER,"
		+ KEY_ORDER +" INTEGER,"
		+ KEY_FLAGS +" TEXT,"
		+ KEY_TYPE_ID +" INTEGER NOT NULL"
		+ ");";
	
	private static final String DATABASE_AREAS_CREATE = "create table "+ DATABASE_AREAS_TABLE +" ("+ KEY_ID +" integer primary key, "
		+ KEY_TITLE +" TEXT NOT NULL,"
		+ KEY_LATITUDE +" TEXT,"
		+ KEY_LONGITUDE +" TEXT,"
		+ KEY_DISTANCE +" REAL,"
		+ KEY_UPDATED +" TEXT"
		+ ");";
	
	public static class TagRow {
        	public long id = -1;
        	public String title;
        	public String scienceName;
        	public String[] commonNames;
        	public String imagePath;
        	public String text;
        	public long areaId;
        	public int order;
        	public String flags;
        	public TagType type;
    }
	
	public static class AreaRow {
		public long id = -1;
        	public String title;
        	public double latitude;
        	public double longitude;
        	public float distance;
        	public String updated;
	}
	
	public TagDatabase(Context context)
	{
		this.context = context;
		dbHelper = new DatabaseHelper(this.context);
	}
	
	public TagDatabase openRead() throws SQLException{
		db = dbHelper.getReadableDatabase();

		return this;
	}
	
	public TagDatabase openWrite() throws SQLException{

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
		if(db.isReadOnly()){
			db.close();
		}else{
			synchronized(dbLock)
			{
				dbHelper.close();
				databaseOpen = false;
				
				dbLock.notify();
			}
		}
	}
	
	public TagRow getTag(int id)
	{		
		Cursor c = db.query(DATABASE_TAGS_TABLE, new String[] {KEY_ID, KEY_TITLE, KEY_IMAGE_URL, KEY_TEXT, KEY_FLAGS, KEY_TYPE_ID}, KEY_ID+"=?", new String[]{String.valueOf(id)}, null, null, null);
		TagRow ret = new TagRow();

		if (c.moveToFirst()) {		
			ret.id = c.getLong(0);
			ret.title = c.getString(1);
			ret.imagePath = c.getString(2);
			ret.text = c.getString(3);
			ret.flags = c.getString(4);
			ret.type = TagType.lookup(c.getInt(5));
		}
		
		c.close();
		
		return ret;
	}
	
	
	public long insertTag(TagRow row)
	{		
		ContentValues vals = new ContentValues();
		vals.put(TagDatabase.KEY_TITLE, row.title);
		vals.put(TagDatabase.KEY_IMAGE_URL, row.imagePath);
		vals.put(TagDatabase.KEY_TEXT, row.text);
		vals.put(TagDatabase.KEY_AREA_ID, row.areaId);
		vals.put(TagDatabase.KEY_ORDER, row.order);
		vals.put(TagDatabase.KEY_FLAGS, row.flags);
		vals.put(TagDatabase.KEY_TYPE_ID, row.type.value());
		
		long rowid = db.insert(DATABASE_TAGS_TABLE, null, vals);
		return rowid;
	}
	
	public boolean clearTags(long areaId)
	{
		// Remove thumbnails
		Cursor c = db.query(DATABASE_TAGS_TABLE, new String[]{KEY_ID, KEY_IMAGE_URL}, KEY_AREA_ID +"=?", new String[]{String.valueOf(areaId)}, null, null, null);
		
		while(c.moveToNext()){
			String path = c.getString(c.getColumnIndex(TagDatabase.KEY_IMAGE_URL));
			new File(path).delete();
		}
		
		c.close();
		
		int count = db.delete(DATABASE_TAGS_TABLE, KEY_AREA_ID +"=?", new String[]{String.valueOf(areaId)});
		
		if(count > 0)
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	public int getTagsAvailable(long areaId, TagType type){
		Cursor c = getTags(areaId, type);
		
		int available = c.getCount();
		
		c.close();
		
		return available;
	}
	
	public Vector<Integer> getAreaTagIDsFromTagID(int tagID){
		Cursor b = db.query(DATABASE_TAGS_TABLE, new String[]{KEY_AREA_ID}, KEY_ID + "=?",new String[]{String.valueOf(tagID)}, null, null, null);
		b.moveToNext();
		int areaId = b.getInt(b.getColumnIndex(KEY_AREA_ID));			
		b.close();
		
		Cursor c = db.query(DATABASE_TAGS_TABLE, new String[]{KEY_ID}, KEY_AREA_ID + "=?",new String[]{String.valueOf(areaId)}, null, null, KEY_ORDER);
		Vector<Integer> keys = new Vector<Integer>();
		
		while(c.moveToNext()){					
			keys.add(c.getInt(c.getColumnIndex(KEY_ID)));			
		}		
		c.close();
		return keys;
	}
	
	public Cursor getTags(long areaId, TagType type){
	    String where = KEY_AREA_ID + "=?";
        String[] whereargs;
        
        if(type == null) {
            whereargs = new String[]{String.valueOf(areaId)};
        } else {
            where += " AND " + KEY_TYPE_ID + "=?";
            whereargs = new String[]{String.valueOf(areaId), String.valueOf(type.value())};
        }

		SQLiteDatabase db = dbHelper.getReadableDatabase();		 
		return db.query(DATABASE_TAGS_TABLE, new String[]{KEY_ID, KEY_TITLE, KEY_IMAGE_URL, KEY_TEXT, KEY_FLAGS}, where, whereargs, null, null, KEY_ORDER);
	}
	
	public long insertArea(AreaRow row)
	{
		TimeZone timeZone = TimeZone.getTimeZone("UTC");  
		Calendar c = Calendar.getInstance(timeZone);
		Date d = c.getTime();
		
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
		
		ContentValues vals = new ContentValues();
		vals.put(TagDatabase.KEY_TITLE, row.title);
		vals.put(TagDatabase.KEY_LATITUDE, row.latitude);
		vals.put(TagDatabase.KEY_LONGITUDE, row.longitude);
		vals.put(TagDatabase.KEY_DISTANCE, row.distance);
		vals.put(TagDatabase.KEY_UPDATED, formatter.format(d));
		vals.put(TagDatabase.KEY_ID, row.id);
		
		return db.insert(DATABASE_AREAS_TABLE, null, vals);
	}
	
	public long updateAreaDistance(long id, double distance)
	{		
		ContentValues vals = new ContentValues();
		vals.put(TagDatabase.KEY_DISTANCE, distance);
		//vals.put(TagDatabase.KEY_UPDATED, "");
		
		return db.update(DATABASE_AREAS_TABLE, vals, TagDatabase.KEY_ID +"=?", new String[]{String.valueOf(id)});
	}
	
	public boolean clearAreas()
	{
		int count = db.delete(DATABASE_AREAS_TABLE, null, null);
		
		if(count > 0)
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	public AreaRow getArea(long id){
		AreaRow row = null;
		
		Cursor cursor = db.query(DATABASE_AREAS_TABLE, new String[]{KEY_ID, KEY_TITLE, KEY_LATITUDE, KEY_LONGITUDE, KEY_DISTANCE, KEY_UPDATED}, KEY_ID+"=?", new String[]{String.valueOf(id)}, null, null, null);
		
		if(cursor.moveToNext()){
			row = new AreaRow();
			
			row.id = cursor.getLong(cursor.getColumnIndex(KEY_ID));
			row.title = cursor.getString(cursor.getColumnIndex(KEY_TITLE));
			row.latitude = cursor.getDouble(cursor.getColumnIndex(KEY_LATITUDE));
			row.longitude = cursor.getDouble(cursor.getColumnIndex(KEY_LONGITUDE));
			row.distance = cursor.getFloat(cursor.getColumnIndex(KEY_DISTANCE));
			row.updated = cursor.getString(cursor.getColumnIndex(KEY_UPDATED));
		}
		
		cursor.close();
		
		return row;
	}
	
	public Cursor getAreas(){
		return getAreas(-1);
	}
	
	public Cursor getAreas(int limit){
		SQLiteDatabase db = dbHelper.getReadableDatabase();

		if(limit==-1)
			return db.query(DATABASE_AREAS_TABLE, new String[]{KEY_ID, KEY_TITLE, KEY_LATITUDE, KEY_LONGITUDE, KEY_DISTANCE, KEY_UPDATED}, null, null, null, null, KEY_DISTANCE +" ASC");
		else
			return db.query(DATABASE_AREAS_TABLE, new String[]{KEY_ID, KEY_TITLE, KEY_LATITUDE, KEY_LONGITUDE, KEY_DISTANCE, KEY_UPDATED}, null, null, null, null, KEY_DISTANCE +" ASC", String.valueOf(limit));
	}
	
	public Cursor getAreasByName(){
		return getAreasByName(-1);
	}
	
	public Cursor getAreasByName(int limit){
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		
		if(limit==-1)
			return db.query(DATABASE_AREAS_TABLE, new String[]{KEY_ID, KEY_TITLE, KEY_LATITUDE, KEY_LONGITUDE, KEY_DISTANCE, KEY_UPDATED}, null, null, null, null, KEY_TITLE +" ASC");
		else
			return db.query(DATABASE_AREAS_TABLE, new String[]{KEY_ID, KEY_TITLE, KEY_LATITUDE, KEY_LONGITUDE, KEY_DISTANCE, KEY_UPDATED}, null, null, null, null, KEY_TITLE +" ASC", String.valueOf(limit));
	}
	
	private static class DatabaseHelper extends SQLiteOpenHelper
	{
		DatabaseHelper(Context ctx)
		{
			super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_TAGS_CREATE);		
			db.execSQL(DATABASE_AREAS_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		    if(oldVersion < 2)
		    {
		        db.execSQL("alter table " + DATABASE_TAGS_TABLE + " rename to " + DATABASE_TAGS_TABLE + "_old");
		        db.execSQL(DATABASE_TAGS_CREATE);
		        
		        Cursor cursor = db.query(DATABASE_TAGS_TABLE + "_old", new String[]{KEY_ID, KEY_TITLE, KEY_IMAGE_URL, KEY_TEXT, KEY_FLAGS, KEY_AREA_ID, KEY_ORDER, "type"}, null, null, null, null, KEY_ORDER);
		        
		        while (cursor.moveToNext()) {		            
		            ContentValues vals = new ContentValues();
		            vals.put(TagDatabase.KEY_TITLE, cursor.getString(1));
		            vals.put(TagDatabase.KEY_IMAGE_URL, cursor.getString(2));
		            vals.put(TagDatabase.KEY_TEXT, cursor.getString(3));
		            vals.put(TagDatabase.KEY_FLAGS, cursor.getString(4));
		            vals.put(TagDatabase.KEY_AREA_ID, cursor.getLong(5));
		            vals.put(TagDatabase.KEY_ORDER, cursor.getInt(6));
		            vals.put(TagDatabase.KEY_TYPE_ID, TagType.WEED.value());
		            
		            db.insert(DATABASE_TAGS_TABLE, null, vals);
		        }
		        
		        cursor.close();
		        
		        db.execSQL("drop table " + DATABASE_TAGS_TABLE + "_old");
		    }
		    
		    if(oldVersion < 3)
		    {
		        db.execSQL("alter table " + DATABASE_TAGS_TABLE + " rename to " + DATABASE_TAGS_TABLE + "_old");
                db.execSQL(DATABASE_TAGS_CREATE);
                
                Cursor cursor = db.query(DATABASE_TAGS_TABLE + "_old", new String[]{KEY_ID, KEY_TITLE, KEY_IMAGE_URL, KEY_TEXT, KEY_FLAGS, KEY_AREA_ID, KEY_ORDER, KEY_TYPE_ID}, null, null, null, null, KEY_ORDER);
                
                while (cursor.moveToNext()) {                   
                    ContentValues vals = new ContentValues();
                    vals.put(TagDatabase.KEY_TITLE, cursor.getString(1));
                    vals.put(TagDatabase.KEY_SCIENCE_NAME, (String)null);
                    vals.put(TagDatabase.KEY_COMMON_NAMES, (String)null);
                    vals.put(TagDatabase.KEY_IMAGE_URL, cursor.getString(2));
                    vals.put(TagDatabase.KEY_TEXT, cursor.getString(3));
                    vals.put(TagDatabase.KEY_FLAGS, cursor.getString(4));
                    vals.put(TagDatabase.KEY_AREA_ID, cursor.getLong(5));
                    vals.put(TagDatabase.KEY_ORDER, cursor.getInt(6));
                    vals.put(TagDatabase.KEY_TYPE_ID, cursor.getInt(7));
                    
                    db.insert(DATABASE_TAGS_TABLE, null, vals);
                }
                
                cursor.close();
                
                db.execSQL("drop table " + DATABASE_TAGS_TABLE + "_old");
		    }
		}		
	}
}
