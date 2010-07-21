package edu.ucla.cens.whatsinvasive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class NoteEdit extends Activity {

    EditText mBodyText;
    int mRowId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.note_edit);
        
        mBodyText = (EditText) findViewById(R.id.body);
        
        Button confirmButton = (Button) findViewById(R.id.confirm);
        Button cancelButton = (Button) findViewById(R.id.cancel);
        
        Bundle extras = getIntent().getExtras();
        
        mRowId = -1;
        
        mBodyText.setText("");
        
        if(savedInstanceState != null) {
            mRowId = savedInstanceState.getInt("id");
            mBodyText.setText(savedInstanceState.getString("note"));
        } else if(extras != null) {
            String body = extras.getString("note");
            mRowId = extras.getInt("id");
            
            if(body != null) {
                mBodyText.setText(body);
            }
        }
        
        confirmButton.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View v) {
                Bundle bundle = new Bundle();
                bundle.putString("note", mBodyText.getText().toString());
                if(mRowId != -1)
                    bundle.putInt("id", mRowId);
                
                Intent i = new Intent();
                i.putExtras(bundle);
                setResult(RESULT_OK, i);
                finish();
            }
        });
        
        cancelButton.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("id", mRowId);
        outState.putString("note", mBodyText.getText().toString());
    }
}
