package edu.ucla.cens.whatsinvasive;

import java.util.List;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

import edu.ucla.cens.whatsinvasive.data.TagDatabase;
import edu.ucla.cens.whatsinvasive.tools.Maps.CustomItemizedOverlay;
import edu.ucla.cens.whatsinvasive.tools.Maps.CustomOverlayItem;

public class AreaMap extends MapActivity {
	private final int MAX_PLACEMARKS = 10;
	
	public static final int RESULT_AREA_SELECTED = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		
		this.setContentView(R.layout.area_map);
		
		MapView map = (MapView) this.findViewById(R.id.MapView01);
		map.getController().setZoom(7);
		//set to center of california
		Double latitude = 37.232953*1E6;
		Double longitude = -119.697876*1E6;
		map.getController().setCenter(new GeoPoint(latitude.intValue(),longitude.intValue()));
		map.setBuiltInZoomControls(true);
        
        TagDatabase db = new TagDatabase(this);
		db.openRead();
		
		Cursor areas = db.getAreas();

		AreaMapOverlay overlay = new AreaMapOverlay(this.getResources().getDrawable(R.drawable.btn_rating_star_off_pressed));
		
		while(areas.moveToNext() && areas.getPosition() < MAX_PLACEMARKS){
			long id = areas.getLong(areas.getColumnIndex(TagDatabase.KEY_ID));
			String title = areas.getString(areas.getColumnIndex(TagDatabase.KEY_TITLE));
			
			Double lat = areas.getDouble(areas.getColumnIndex(TagDatabase.KEY_LATITUDE)) * 1E6;
			Double lon = areas.getDouble(areas.getColumnIndex(TagDatabase.KEY_LONGITUDE)) * 1E6;
			
			GeoPoint point = new GeoPoint(lat.intValue(), lon.intValue());
			CustomOverlayItem item = new CustomOverlayItem(point, title, "");
			item.id = id;
			item.latitude = areas.getDouble(areas.getColumnIndex(TagDatabase.KEY_LATITUDE));
			item.longitude = areas.getDouble(areas.getColumnIndex(TagDatabase.KEY_LONGITUDE));	
			
			overlay.addOverlay(item);
		}
		
		List<Overlay> overlays = map.getOverlays();
		overlays.add(overlay);
		
		areas.close();
		db.close();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		//should return nothing on back button
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			this.setResult(RESULT_OK);
			this.finish();
			return true;
		}
		return false;
	}

	@Override
	protected boolean isRouteDisplayed() {
		// TODO Auto-generated method stub
		return false;
	}
	
	private class AreaMapOverlay extends CustomItemizedOverlay {

		public AreaMapOverlay(Drawable defaultMarker) {
			super(AreaMap.this, defaultMarker);
			// TODO Auto-generated constructor stub
		}

		@Override
		protected boolean onTap(int index) {
			CustomOverlayItem item = (CustomOverlayItem) this.getItem(index);
			
			Intent intent = new Intent();
			intent.putExtra("id", item.id);
			intent.putExtra("latitude", item.latitude);
			intent.putExtra("longitude", item.longitude);
			
			AreaMap.this.setResult(RESULT_AREA_SELECTED, intent);
			AreaMap.this.finish();

			return super.onTap(index);
		}

		@Override
		public boolean onTouchEvent(MotionEvent event, MapView mapView) {
			switch(event.getAction()){
				case MotionEvent.ACTION_DOWN:
					// TODO Show title as overlay
					break;
				case MotionEvent.ACTION_UP:
					break;
			}
			
			return super.onTouchEvent(event, mapView);
		}

	}
}
