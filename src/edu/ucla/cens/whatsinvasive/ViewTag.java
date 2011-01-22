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
		TextView tag = (TextView) this.findViewById(R.id.tag_name);
		TextView tagged = (TextView) this.findViewById(R.id.tag_time);
		TextView uploaded = (TextView) this.findViewById(R.id.tag_time);
		TextView location = (TextView) this.findViewById(R.id.tag_location);
		TextView notes = (TextView)this.findViewById(R.id.tag_note);
		
		ImageView thumbnail = (ImageView) this.findViewById(R.id.tag_image);
		
		tag.setText(data.tagsValue);
		tagged.setText(getString(R.string.tag_tagged_at) + " " + data.timeValue);
		if(data.uploadValue!=null)
			uploaded.setText(getString(R.string.tag_uploaded_on) + " " + data.uploadValue);
		if(data.latValue != 0)
			location.setText(String.format("%s %.4f, %.4f ± %.2f m", getString(R.string.tag_location), 
			        data.latValue, data.lonValue, data.accuracyValue));
		if(data.noteValue != null)
		    notes.setText(getString(R.string.tag_note) + " " + data.noteValue);
		
		if(data.filenameValue != null && new File(data.filenameValue).exists()){
			thumbnail.setVisibility(ImageView.VISIBLE);
			thumbnail.setImageBitmap(BitmapFactory.decodeFile(data.filenameValue));
		}else{
			thumbnail.setVisibility(ImageView.GONE);
		}
	}
}
