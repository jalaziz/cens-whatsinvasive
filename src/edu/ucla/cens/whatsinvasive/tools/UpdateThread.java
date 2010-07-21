package edu.ucla.cens.whatsinvasive.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Observable;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.client.utils.URLEncodedUtils;

import android.content.Context;

public abstract class UpdateThread extends Thread {
	protected Context context;
	
	protected Observable observable;
	private boolean updateAvailable = false;
	private String className;
	
	public UpdateThread(Context context){
		this.context = context;
		this.className = this.getClass().getSimpleName();
			
		observable = new CustomObservable();
	}
	
	protected StringBuilder sendRequest(String baseUrl, String path, List<NameValuePair> qparams) throws SocketTimeoutException{
		URI uri = null;
		try {
			uri = URIUtils.createURI("http", baseUrl, 80, path, URLEncodedUtils.format(qparams, "UTF-8"), null);
		} catch (URISyntaxException e) {}
		
		HttpClient httpClient = new CustomHttpClient();
		HttpGet request = new HttpGet(uri);
		
		HttpResponse response;
		try {
			response = httpClient.execute(request);

			int status = response.getStatusLine().getStatusCode();

			if (status == HttpStatus.SC_OK) {
				InputStream is = response.getEntity().getContent();

				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				StringBuilder sb = new StringBuilder();

				String line = null;
				try {
					while ((line = reader.readLine()) != null) {
						sb.append(line + "\n");
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						is.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				return sb;
			} else if(status == HttpStatus.SC_NOT_MODIFIED){
				// Do nothing for now
			} else {
				// Something went wrong
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (SocketTimeoutException e){
			throw e;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	protected void setChanged(UpdateData arg){
		this.updateAvailable = true;
		
		this.observable.notifyObservers(arg);
	}
	
	public class UpdateData {
		public String source = className;
		public String description;
		
		public int progress = 0;
		public int total = 0;
		public boolean allDone = false;
		
		public UpdateData(){}
		
		public UpdateData(boolean allDone){
			this.allDone = allDone;
		}
		
		public UpdateData(String description, boolean allDone){
			this(allDone);
			
			this.description = description;
		}
		
		public UpdateData(String description, int progress, int total){
			this(description, false);
			
			this.progress = progress;
			this.total = total;
		}
	}
	
	protected class CustomObservable extends Observable{
		@Override
		public void notifyObservers() {
			// TODO Auto-generated method stub
			if(updateAvailable)
				this.setChanged();
			
			super.notifyObservers();
			
			updateAvailable = false;
		}

		@Override
		public void notifyObservers(Object data) {
			if(updateAvailable)
				this.setChanged();
			
			super.notifyObservers(data);
			
			updateAvailable = false;
		}
	}
	
	public Observable getObservable(){
		 return observable;
	}
}
