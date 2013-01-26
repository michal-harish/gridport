package co.gridport.server;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Enumeration;


public class SubRequestMerge extends SubRequest {
	
	public SubRequestMerge(ClientThread t, String url, String async_status) throws MalformedURLException, IOException {
		super(t, url, async_status);
		conn.setRequestMethod(t.request.getMethod());			
		if (async_status == null || async_status.equals(""))
			synchronized(income.tasks) { income.tasks.add(this); }
		else {
			async_statusCode = Integer.valueOf(async_status);
			synchronized(income.events) { income.events.add(this); }
		}
		log.debug(conn.getRequestMethod() + " " + conn.getURL().toString() );
		conn.setDoInput(true);
		
		//Replicate REQUEST Headers into SUBREQUEST Headers
		for(Enumeration<String> headers = income.request.getHeaderNames();
		    headers.hasMoreElements();
	    )
		{
		    String header = headers.nextElement();
    		if (header == null) continue;    		
    		if (conn.getRequestProperties().containsKey("Host") && header.equals("Host")) continue;	    		
    		if (header.equals("X-forwarded-host"))
		    {
    		    log.debug("IGNORE HEADER " + header);
    		    continue; // THIS WOULD STACK THE GATEWAY TO THE X_HTTP_HOST
		    }
    		    		
    		if (header.equals("Transfer-Encoding")) 
		    {
    		    log.debug("IGNORE HEADER " + header);
    		    continue; // THIS CHANGES ON PER CONNECTION BASIS    		
		    }
            for(Enumeration<String> values = income.request.getHeaders(header);
                values.hasMoreElements();
            )
            {
	    		String value = values.nextElement();
	    		log.debug("HEADER " + header + ": " + value);
	    		conn.addRequestProperty(header, value);
    		}
    	}
	}
	
	protected void execute() throws IOException {	
		//
	}
	
}
