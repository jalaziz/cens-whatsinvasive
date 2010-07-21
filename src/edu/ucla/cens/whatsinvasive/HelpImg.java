package edu.ucla.cens.whatsinvasive;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class HelpImg extends Activity {

	static final int MAIN_HELP = 0;
	static final int SETTINGS_HELP = 2;
	static final int TAG_HELP = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		switch (this.getIntent().getIntExtra("help type", MAIN_HELP)) {
		case MAIN_HELP:
			setTitle(R.string.title_helpimg);
			this.setContentView(R.layout.help);
			break;
		case TAG_HELP:
			setTitle(R.string.title_tagginghelp);
			this.setContentView(R.layout.tag_help2);
			break;
		case SETTINGS_HELP:
			setTitle(R.string.title_settingshelp);
			this.setContentView(R.layout.settings_help);
			break;
		}

		findViewById(R.id.Button01).setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				HelpImg.this.setResult(RESULT_OK);
				HelpImg.this.finish();
			}
		});
	}

}
