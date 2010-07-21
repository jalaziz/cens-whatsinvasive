package edu.ucla.cens.whatsinvasive;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import edu.ucla.cens.whatsinvasive.tools.CustomHttpClient;

public class Login extends Activity {
	private final String VALIDATE_URL = "http://sm.whatsinvasive.com/phone/auth_post.php";
	private final String REGISTER_URL = "http://sm.whatsinvasive.com/phone/register.php";
	
	private final int DIALOG_LOGGING = 0;
	private final int DIALOG_TIMEOUT = 1;
	
	private final int MESSAGE_INVALID_LOGIN = 0;
	private final int MESSAGE_LOGIN = 1;
	private final int MESSAGE_LOGIN_TIMEOUT = 2;
	private final int MESSAGE_LOGIN_RETRY = 3;
	private final int MESSAGE_LOGIN_CANCEL = 4;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);		 
		
		setupEvents();
	}
	
	private void setupEvents(){
		final Button button = (Button) this.findViewById(R.id.ButtonRegister);
		final Button buttonr = (Button) this.findViewById(R.id.ButtonReset);
		final Button buttonl = (Button) this.findViewById(R.id.ButtonLogin);
		final Button buttont = (Button) this.findViewById(R.id.ButtonTest);
		button.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse(REGISTER_URL));
				startActivity(intent);
			}});
		
		
		buttonr.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				((EditText) Login.this.findViewById(R.id.EditText01)).setText("");
				((EditText) Login.this.findViewById(R.id.EditText02)).setText("");
			}});
	
		buttonl.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				validateLogin();
			}});
		
		buttont.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				((EditText) Login.this.findViewById(R.id.EditText01)).setText("test");
				((EditText) Login.this.findViewById(R.id.EditText02)).setText("test");
				validateLogin();
			}});
		
		final EditText username = ((EditText) this.findViewById(R.id.EditText01));	
		final EditText password = ((EditText) this.findViewById(R.id.EditText02));
		username.setOnKeyListener(new OnKeyListener() {

			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)){ 
					buttonl.performClick();
					return true;
				}

				return false;
			}
			
		});
		
		password.setOnKeyListener(new OnKeyListener() {

			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)){
					buttonl.performClick();
					return true;
				}

				return false;
			}
			
		});
	}
	
	private void validateLogin(){
		final String username = ((EditText) this.findViewById(R.id.EditText01)).getText().toString();
		final String password = ((EditText) this.findViewById(R.id.EditText02)).getText().toString();
		
		if(username.equals("") || password.equals("")) {
			Toast.makeText(this, getString(R.string.login_username_must_not_empty), Toast.LENGTH_LONG).show();
		} else {
			this.showDialog(DIALOG_LOGGING);
			
			Thread thread = new Thread() {
				@Override
				public void run() {
					HttpClient httpClient = new CustomHttpClient();
					HttpGet request = new HttpGet(VALIDATE_URL +"?username="+ username +"&password="+ password);
					
					try {
						HttpResponse response = httpClient.execute(request);

						if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
							Message msg = new Message();
							msg.what = MESSAGE_LOGIN;
							msg.obj = new String[]{username, password};
							
							Login.this.handler.sendMessage(msg);
						}else{
							// Failed to get data
							Login.this.handler.sendEmptyMessage(MESSAGE_INVALID_LOGIN);
						}
					} catch (ClientProtocolException e) {
						Login.this.handler.sendEmptyMessage(MESSAGE_LOGIN_TIMEOUT);
						e.printStackTrace();
					} catch(SocketTimeoutException e) {
						//java.net.SocketTimeoutException: The operation timed out
						//java.net.SocketTimeoutException: Socket is not connected
						Login.this.handler.sendEmptyMessage(MESSAGE_LOGIN_TIMEOUT);
					} catch (IOException e) {
						Login.this.handler.sendEmptyMessage(MESSAGE_LOGIN_TIMEOUT);
						e.printStackTrace();
					}
				}
			};

			thread.start();
		}
	}
	
	private final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg){
			switch(msg.what){
				case MESSAGE_INVALID_LOGIN:
					Login.this.dismissDialog(DIALOG_LOGGING);
					Toast.makeText(Login.this, getString(R.string.login_invalid), Toast.LENGTH_SHORT).show();
					
					break;
				case MESSAGE_LOGIN:
					SharedPreferences preferences = Login.this.getSharedPreferences(WhatsInvasive.PREFERENCES_USER, Activity.MODE_PRIVATE);
			        
					Editor edit = preferences.edit();
					
					String[] values = (String[]) msg.obj;
					
					edit.putString("username", values[0]);
					edit.putString("password", values[1]);
					
					edit.commit();

			        	Login.this.setResult(Activity.RESULT_OK);
					Login.this.finish();
					break;
				case MESSAGE_LOGIN_TIMEOUT:
					Login.this.dismissDialog(DIALOG_LOGGING);
					Login.this.showDialog(DIALOG_TIMEOUT);
					break;
				case MESSAGE_LOGIN_RETRY:
					Login.this.dismissDialog(DIALOG_TIMEOUT);
					validateLogin();
					break;
				case MESSAGE_LOGIN_CANCEL:
				    Login.this.dismissDialog(DIALOG_TIMEOUT);
				    Login.this.setResult(Activity.RESULT_CANCELED);
					Login.this.finish();
					break;
			}
		}
	};
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		
		switch(id){
			case DIALOG_LOGGING:
				ProgressDialog progress = new ProgressDialog(this);
				
				progress.setTitle(getString(R.string.login_loading));
				progress.setMessage(getString(R.string.login_please_wait));
				progress.setIndeterminate(true);
				progress.setCancelable(false);
				
				dialog = progress;
				
				break;
			case DIALOG_TIMEOUT:
				dialog = new AlertDialog.Builder(this)
					.setTitle(getString(R.string.login_timeout_title))
					.setMessage(getString(R.string.login_timeout_msg))
					.setPositiveButton(getString(R.string.login_retry_button), new DialogInterface.OnClickListener(){

						public void onClick(DialogInterface dialog, int which) {
							Login.this.handler.sendEmptyMessage(MESSAGE_LOGIN_RETRY);
						}})
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener(){

						public void onClick(DialogInterface dialog, int which) {
							Login.this.handler.sendEmptyMessage(MESSAGE_LOGIN_CANCEL);
						}})
					
					.create();
				break;
		}
			
		return dialog;
	}
}
