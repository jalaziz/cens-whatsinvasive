package edu.ucla.cens.whatsinvasive;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.Window;

public class Splash extends Activity {
    private boolean m_firstRun = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.splash);

        SharedPreferences preferences = this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);

        m_firstRun = preferences.getBoolean("firstRun", true);		

        new Handler().postDelayed(new Runnable() {
            public void run() {
                startNextActivity();
            }
        }, 2000);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            startNextActivity();
        }
        return true;
    }

    private synchronized void startNextActivity() {
        Intent intent;
        
        if(m_firstRun) {
            intent = new Intent(this, Welcomer.class);
        } else {
            intent = new Intent(this, WhatsInvasive.class); 
        }
        
        this.startActivity(intent);
        Splash.this.finish();
    }
}
