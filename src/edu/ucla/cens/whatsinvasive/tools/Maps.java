package edu.ucla.cens.whatsinvasive.tools;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;

public class Maps {
	public static class CustomOverlayItem extends OverlayItem {
		public long id;
		public double latitude;
		public double longitude;
		
		public CustomOverlayItem(GeoPoint point, String title, String snippet) {
			super(point, title, snippet);
			// TODO Auto-generated constructor stub
		}
	}
	
	public abstract static class CustomItemizedOverlay extends ItemizedOverlay<OverlayItem> {
		private final Context context;
		public CustomItemizedOverlay(Context context, Drawable defaultMarker) {
			super(boundCenterBottom(defaultMarker));
			
			this.context = context;
		}

		private final ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
		
		public void addOverlay(OverlayItem overlay) {
		    mOverlays.add(overlay);
		    populate();
		}
		
		@Override
		protected OverlayItem createItem(int i) {
			return mOverlays.get(i);
		}

		@Override
		public int size() {
			return mOverlays.size();
		}		
	}
}
