package edu.ucla.cens.whatsinvasive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class Welcomer extends Activity {
 
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.welcomer);
		setTitle(R.string.title_welcomer);

		this.findViewById(R.id.Button01).setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
			    Welcomer.this.startActivity(new Intent(Welcomer.this, WhatsInvasive.class));
                Welcomer.this.finish();
			}
			
		});
		
	}

}