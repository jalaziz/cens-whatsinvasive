package edu.ucla.cens.whatsinvasive;

import java.io.File;
import java.util.Vector;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import edu.ucla.cens.android.ui.WorkspaceView;
import edu.ucla.cens.whatsinvasive.data.TagDatabase;
import edu.ucla.cens.whatsinvasive.data.TagDatabase.TagRow;
import edu.ucla.cens.whatsinvasive.tools.Media;
import edu.ucla.cens.whatsinvasive.tools.Media.Size;

public class TagHelp extends Activity {
	private WorkspaceView mWorkspace;
    
    private TagDatabase mDb;
    private int mId;
    private TagType mType;
    private Vector<Integer> mIds;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mWorkspace = new WorkspaceView(this, null);
		this.setContentView(mWorkspace);
		
		if(savedInstanceState != null && savedInstanceState.containsKey("id"))
		    mId = savedInstanceState.getInt("id");
		else
		    mId = this.getIntent().getIntExtra("id", -1);
		
		if(savedInstanceState != null && savedInstanceState.containsKey("type"))
		    mType = (TagType)savedInstanceState.getSerializable("type");
		else
		    mType = (TagType)this.getIntent().getSerializableExtra("type");
		
		mDb = new TagDatabase(this);
		mDb.openRead();
	}

    @Override
    protected void onResume() {
        super.onResume();
        
        mIds = mDb.getAreaTagIDsFromTagID(mId, mType);
        
        for(int id: mIds) {
            addTagView(id);
        }
        
        mWorkspace.setCurrentScreen(mIds.indexOf(mId));
    }
    
    @Override
    protected void onPause() {
        mId = mIds.get(mWorkspace.getCurrentScreen());
        
        mWorkspace.removeAllViews();
        
        super.onPause();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("id", mId);
        outState.putSerializable("type", mType);
    }
    
    @Override
    protected void onDestroy() {
        mDb.close();
        
        super.onDestroy();
    }

    private int getNextId(int delta) {
	    int i = mIds.size() + (mIds.indexOf(mId) + delta);
        i %= mIds.size();
	    
        return mIds.get(i);
	}
	
	private int getNextLoadId(int delta) {
	    int i = mIds.size() + (mIds.indexOf(mId) + (2 * delta));
        i %= mIds.size();
        
        return mIds.get(i); 
	}
	
	private void addTagView(int id) {
	    View tagView = getLayoutInflater().inflate(R.layout.tag_help, null);
        mDb.openRead();
        
        TagRow tag = mDb.getTag(id);
        
        mDb.close();
        
        TextView title = (TextView) tagView.findViewById(R.id.title);
        title.setText(tag.title);
        
        TextView scienceName = (TextView) tagView.findViewById(R.id.science_name);
        scienceName.setText(tag.scienceName);
        
        TextView commonNames = (TextView) tagView.findViewById(R.id.common_names);
        if(tag.commonNames != null && tag.commonNames.length > 1) {
            String names = StringUtils.join(ArrayUtils.remove(tag.commonNames, 0), ',');
            
            commonNames.setVisibility(View.VISIBLE);
            commonNames.setText(getString(R.string.common_names_prefix) + " " + names);
        } else {
            commonNames.setVisibility(View.GONE);
        }
        
        TextView text = (TextView) tagView.findViewById(R.id.description);
        if(tag.text!=null){
            text.setText(tag.text);
            text.setVisibility(TextView.VISIBLE);
        }else{
            text.setVisibility(TextView.GONE);
        }
        
        if(tag.imagePath!=null && new File(tag.imagePath).exists()){
            int width = this.getWindowManager().getDefaultDisplay().getWidth();
            int height = this.getWindowManager().getDefaultDisplay().getHeight();
            
            Size size = new Media.Size(width, height);
            size.exact = true;
            
            Bitmap bitmap = Media.resizeImage(tag.imagePath, size);
            
            ImageView image = (ImageView) tagView.findViewById(R.id.ImageView01);
            image.setImageBitmap(bitmap);
        }
        
        mWorkspace.addView(tagView);
	}
}
