package co.gridport.server.router;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.utils.Utils;

public abstract class SubRequest extends Thread { 
    static protected Logger log = LoggerFactory.getLogger("subrequest");
    
	protected ProxyRequestThread income;	
	protected HttpURLConnection conn;
	
	public Exception error = null;
	public Map<String, List<String>> headers;
	public int statusCode;
	public int async_statusCode; // from settings
	public String verbs;// from settings
	public long received;
	public double runtime;
	
	public String name = null;
	public String base = null;
	
	private OutputStream ost = null;
	
	public SubRequest( ProxyRequestThread t, String url, String async_status) throws MalformedURLException, IOException {
		income = t;
		received = System.currentTimeMillis();
		
		//adding trailing slash to prevent default 301 from http servers	
		url = url.replaceFirst("^(.*/)?([^/\\.\\?]+)(\\?.*)?$","$1$2/$3");	 

		URL U = new URL(url);		
		conn = (HttpURLConnection) U.openConnection();
		conn.setConnectTimeout(Math.max(1000, 1000 * 6)); //6 seconds connection timeout
		conn.setReadTimeout(1000 * 30);
		conn.setAllowUserInteraction(false);
		conn.setUseCaches(false);
		conn.setInstanceFollowRedirects(false);									
		//conn.setRequestProperty("Accept", income.exchange.getRequestHeaders().getFirst("Accept"));
		//conn.setRequestProperty("User-Agent", income.exchange.getRequestHeaders().getFirst("User-Agent"));		
		
		conn.setRequestProperty("Host", conn.getURL().getHost());	
		log.debug("FORCED HEADER Host = " + conn.getURL().getHost());
		if (income.context.getUsername()!=null && !Utils.blank(income.context.getUsername())) {
    		conn.setRequestProperty("Authorization-user",income.context.getUsername());
    		conn.setRequestProperty("Authorization-realm",income.context.getRealm());
    	}		
	}
	
    public String getURL() {
        return conn.getURL().toString();
    }

    public String getRequestMethod() {
        return conn.getRequestMethod();
    }

	
	protected void writeOut(byte[] buffer, int len) throws IOException
	{
		if (ost == null)
		{
			conn.setDoOutput(true);
			ost = conn.getOutputStream();
		}
		ost.write(buffer, 0, len);		
	}
	
	public void run() {		
		try {
			
			//if output buffer was open and written, close and flush it
			if (ost != null)
			{
				ost.flush();
				ost.close();
			}
			
			//exectue custom subrequest behaviour
			execute();	
			
			//read response headers
			headers =	conn.getHeaderFields();
			statusCode = conn.getResponseCode();	
			log.debug("RESPONSE  STATUS=" + statusCode);
			
			for(String header: headers.keySet()) {
			    for(String value: headers.get(header)) {
			        log.debug("RESPONSE HEADER " + header +": " + value);
			    }
			}
			//response_len = -1;
			
			if (statusCode == 202 ) return; //Accepted
			if (statusCode == 204 ) return; //No content
			if (statusCode == 304 ) return; //Not Modified					
						
		} catch (UnknownHostException e) {			
			//TODO QUALITY add to list of unknown hosts cache and attach clearing to the flush command			
			error = e;
		} catch (Exception e) {
			log.debug("Subrequest '"+this.name+"' failed: " + e.getMessage());
			error = e;						
		} finally {
			runtime = (double) ( System.currentTimeMillis() - received ) / 1000 ;
		}
	}
	
	abstract protected void execute() throws IOException;    
	
}
