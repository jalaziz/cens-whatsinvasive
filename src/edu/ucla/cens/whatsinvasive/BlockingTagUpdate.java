package edu.ucla.cens.whatsinvasive;

import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import edu.ucla.cens.whatsinvasive.services.LocationService;
import edu.ucla.cens.whatsinvasive.services.LocationService.TagUpdateThread;
import edu.ucla.cens.whatsinvasive.tools.UpdateThread.UpdateData;

public class BlockingTagUpdate extends Activity implements Observer {
	private static final int MESSAGE_COMPLETE_TAG = 321;
	private static final int DIALOG_DOWNLOAD_TAGS = 3231;
	private TagUpdateThread thread;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.blockingtagupdate); 		

		thread = new TagUpdateThread(BlockingTagUpdate.this, LocationService.getParkId(BlockingTagUpdate.this));
		thread.getObservable().addObserver(BlockingTagUpdate.this);	
		thread.start();
	}

	private final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg){
			switch(msg.what){
			case MESSAGE_COMPLETE_TAG:
				
				BlockingTagUpdate.this.setResult(RESULT_OK);
				BlockingTagUpdate.this.finish();
				break;			
			}
		}
	};
	
	public void update(Observable observable, Object data) {	
		UpdateData update = (UpdateData) data;
		if(update.source.equals("TagUpdateThread")){
			if(update.allDone){
				this.handler.sendEmptyMessage(MESSAGE_COMPLETE_TAG);
			}
			
		}
		
	}
	
}
