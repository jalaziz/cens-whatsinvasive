package edu.ucla.cens.whatsinvasive;

import java.io.File;
import java.util.Vector;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;
import edu.ucla.cens.whatsinvasive.data.TagDatabase;
import edu.ucla.cens.whatsinvasive.data.TagDatabase.TagRow;
import edu.ucla.cens.whatsinvasive.tools.Media;
import edu.ucla.cens.whatsinvasive.tools.Media.Size;

public class TagHelp extends Activity {	
	GestureDetector m_gestureDetector;
	View.OnTouchListener m_gestureListener;
	
	private ViewFlipper m_flipper;
	private View m_leftView;
	private View m_centerView;
	private View m_rightView;
	
	private Animation m_slideLeftIn;
	private Animation m_slideLeftOut;
	private Animation m_slideRightIn;
    private Animation m_slideRightOut;
    
    private TagDatabase m_db;
    private int m_id;
    private Vector<Integer> m_ids;
	
//	@Override
//	public boolean onTouchEvent(MotionEvent event) {
//        	if (m_gestureDetector.onTouchEvent(event))
//        	return true;
//        	else
//        	return false;
//	}

    @Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        	Intent intent = new Intent(this,TagLocation.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
        }
        return false;
    }
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.tag_help_flipper);
		
		m_flipper = (ViewFlipper)this.findViewById(R.id.flipper);
		m_flipper.showNext();
		
		m_leftView = this.findViewById(R.id.left);
		m_centerView = this.findViewById(R.id.center);
		m_rightView = this.findViewById(R.id.right);
		
		m_slideLeftIn = AnimationUtils.loadAnimation(this, R.anim.slide_left_in);
        m_slideLeftOut = AnimationUtils.loadAnimation(this, R.anim.slide_left_out);
        m_slideRightIn = AnimationUtils.loadAnimation(this, R.anim.slide_right_in);
        m_slideRightOut = AnimationUtils.loadAnimation(this, R.anim.slide_right_out);
        
        m_slideLeftIn.setAnimationListener(new AnimationListener() {
            public void onAnimationStart(Animation animation) { }
            public void onAnimationRepeat(Animation animation) { }
            public void onAnimationEnd(Animation animation) { 
                m_flipper.post(new Runnable(){
                    public void run() {
                        switchView(1);
                    }
                });
            }
        });

        m_slideRightIn.setAnimationListener(new AnimationListener() {
            public void onAnimationStart(Animation animation) { }
            public void onAnimationRepeat(Animation animation) { }
            public void onAnimationEnd(Animation animation) {
                m_flipper.post(new Runnable(){
                    public void run() {
                        switchView(-1);
                    }
                });
            }
        });
		
		m_gestureDetector = new GestureDetector(this, new MyGestureDetector());
		
		m_gestureListener = new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (m_gestureDetector.onTouchEvent(event)) {
                    return true;
                }
                return false;
            }
        };
        
		m_leftView.setOnTouchListener(m_gestureListener);
		m_centerView.setOnTouchListener(m_gestureListener);
		m_rightView.setOnTouchListener(m_gestureListener);
		
		m_id = this.getIntent().getIntExtra("id", -1);
		
		m_db = new TagDatabase(this);
		m_db.openRead();
	}

    @Override
    protected void onResume() {
        super.onResume();
        
        m_ids = m_db.getAreaTagIDsFromTagID(m_id);
        
        loadView(m_leftView, getNextId(-1));
        loadView(m_centerView, m_id);
        loadView(m_rightView, getNextId(1));
    }
    
    @Override
    protected void onDestroy() {
        m_db.close();
        
        super.onDestroy();
    }

    private int getNextId(int delta) {
	    int i = m_ids.size() + (m_ids.indexOf(m_id) + delta);
        i %= m_ids.size();
	    
        return m_ids.get(i);
	}
	
	private int getNextLoadId(int delta) {
	    int i = m_ids.size() + (m_ids.indexOf(m_id) + (2 * delta));
        i %= m_ids.size();
        
        return m_ids.get(i); 
	}
	
	private void loadView(View v, int id) {
        m_db.openRead();
        
        TagRow tag = m_db.getTag(id);
        
        m_db.close();
        
        TextView title = (TextView) v.findViewById(R.id.TextView01);
        title.setText(tag.title);
        
        TextView text = (TextView) v.findViewById(R.id.TextView02);
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
            
            ImageView image = (ImageView) v.findViewById(R.id.ImageView01);
            image.setImageBitmap(bitmap);
        }
        
	}
	
	private void switchView(int delta) {
	    int lid = getNextLoadId(delta);
	    
	    if(delta == 0) {
	        //set aliases
//            centerView = text0;
//            nextTextView = text1;
//            previousTextView = text2;
	    } else if (delta == 1) {
	        View currTemp = m_centerView;
            View prevTemp = m_leftView;
            View nextTemp = m_rightView;
	        
            loadView(prevTemp, lid);
            
            //reset aliases 
            m_leftView = currTemp;
            m_centerView = nextTemp;
            m_rightView = prevTemp;
	    } else if (delta == -1) {
	        View currTemp = m_centerView;
            View prevTemp = m_leftView;
            View nextTemp = m_rightView;
            
            loadView(nextTemp, lid);

            //reset aliases
            m_centerView = prevTemp;
            m_rightView = currTemp;
            m_leftView = nextTemp;
	    }
	    
	    m_id = getNextId(delta);
	}
	
	class MyGestureDetector extends SimpleOnGestureListener {
		private static final int SWIPE_MIN_DISTANCE = 120;
		private static final int SWIPE_MAX_OFF_PATH = 250;
		private static final int SWIPE_THRESHOLD_VELOCITY = 200;
		
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){

			if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
			    return false;
			}

			try { 
			    // right to left swipe
			    if(e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
			        m_flipper.setInAnimation(m_slideLeftIn);
                    m_flipper.setOutAnimation(m_slideLeftOut); 
                    m_flipper.showNext();
			    //left to right swipe
			    } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
			        m_flipper.setInAnimation(m_slideRightIn);
                    m_flipper.setOutAnimation(m_slideRightOut); 
                    m_flipper.showPrevious();
			    } 
			} 
			catch (Exception e) { /* nothing */ }

			return true;
		}
	}
}
