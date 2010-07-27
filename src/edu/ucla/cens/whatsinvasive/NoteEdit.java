package edu.ucla.cens.whatsinvasive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class NoteEdit extends Activity {

    TextView mTitle;
    EditText mBodyText;
    int mRowId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.note_edit);
        setTitle(R.string.title_note);
        
        mTitle = (TextView) findViewById(R.id.title);
        mBodyText = (EditText) findViewById(R.id.body);
        
        Button confirmButton = (Button) findViewById(R.id.confirm);
        Button cancelButton = (Button) findViewById(R.id.cancel);
        
        Bundle extras = getIntent().getExtras();
        
        mRowId = -1;
        
        mBodyText.setText("");
        
        if(savedInstanceState != null) {
            mRowId = savedInstanceState.getInt("id");
            mTitle.setText(savedInstanceState.getString("title"));
            mBodyText.setText(savedInstanceState.getString("note"));
        } else if(extras != null) {
            String title = extras.getString("title");
            String body = extras.getString("note");
            mRowId = extras.getInt("id");
            
            if(title != null) {
                mTitle.setText(title);
            }
            
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
        outState.putString("title", mTitle.getText().toString());
    }
}
