package edu.ucla.cens.whatsinvasive.tools;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class CustomHttpClient extends DefaultHttpClient {
	private final int CONNECT_TIMEOUT = 300000;
	private final int SOCKET_TIMEOUT = 300000;
	
	public CustomHttpClient(){
		HttpParams httpParams = this.getParams();
		HttpConnectionParams.setConnectionTimeout(httpParams, CONNECT_TIMEOUT);
		HttpConnectionParams.setSoTimeout(httpParams, SOCKET_TIMEOUT);
	}
}
