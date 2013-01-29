package co.gridport.server;

import java.io.IOException;
import java.net.MalformedURLException;

import co.gridport.server.utils.Utils;

public class SubRequestSend extends SubRequest {
	private String body = null;
	public SubRequestSend(ClientThread t, String url, String method, String entity) throws MalformedURLException, IOException {
		super(t, url, null);
		conn.setRequestMethod(method);					
		conn.setRequestProperty("X-forwarded-host", income.context.getHost() );
		conn.setDoInput(true);
		body = entity;
	}
	public SubRequestSend(ClientThread t, String url, String method) throws MalformedURLException, IOException {
		super(t, url, null);
		conn.setRequestMethod(method);
		conn.setDoInput(true);
		
	}
	protected void execute() throws IOException {			
		if (!Utils.blank(body)) {
			writeOut(body.getBytes(), body.getBytes().length);
		} 
	}
}
