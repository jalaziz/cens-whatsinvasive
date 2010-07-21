package edu.ucla.cens.whatsinvasive;

import java.io.File;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import edu.ucla.cens.whatsinvasive.data.PhotoDatabase;
import edu.ucla.cens.whatsinvasive.data.PhotoDatabase.PhotoDatabaseRow;

public class ViewTag extends Activity {
	private PhotoDatabaseRow data;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.view_tag);
		
		PhotoDatabase pdb = new PhotoDatabase(this);
		
		long id = this.getIntent().getIntExtra("id", -1);
		
		pdb.open();
		data = pdb.fetchPhoto(id);
		pdb.close();
		
		if(data!=null)
			populate();
	}

	private void populate(){
		TextView tag = (TextView) this.findViewById(R.id.TextView01);
		TextView tagged = (TextView) this.findViewById(R.id.TextView02);
		TextView uploaded = (TextView) this.findViewById(R.id.TextView03);
		TextView location = (TextView) this.findViewById(R.id.TextView04);
		TextView notes = (TextView)this.findViewById(R.id.TextView05);
		
		ImageView thumbnail = (ImageView) this.findViewById(R.id.ImageView01);
		
		tag.setText(data.tagsValue);
		tagged.setText(getString(R.string.tag_tagged_at) + " " + data.timeValue);
		if(data.uploadValue!=null)
			uploaded.setText(getString(R.string.tag_uploaded_on) + " " + data.uploadValue);
		if(data.latValue!=null && !data.latValue.equals(""))
			location.setText(getString(R.string.tag_location) + " " + roundString(data.latValue) +", "+ roundString(data.lonValue));
		if(data.noteValue != null)
		    notes.setText(getString(R.string.tag_note) + " " + data.noteValue);
		
		if(data.filenameValue != null && new File(data.filenameValue).exists()){
			thumbnail.setVisibility(ImageView.VISIBLE);
			thumbnail.setImageBitmap(BitmapFactory.decodeFile(data.filenameValue));
		}else{
			thumbnail.setVisibility(ImageView.GONE);
		}
	}
	
	private String roundString(String value){
		double d = Double.parseDouble(value);
		
		return String.valueOf(Math.round(d * 10000) / 10000.0);
	}
}
