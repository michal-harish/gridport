package co.gridport.server.domain;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;




public class RequestContext {
    
    public long receivedTimestampMs;
    public HttpServletRequest request;
    public HttpServletResponse response;
    
	public boolean ssl;
	public int port;
	public String gateway;
	public String gateway_host = null;
	public String consumer_ip;
	public String method ;
	public String URI;
	public String QUERY_STRING;
	public HashMap<String,String> params;
	public ArrayList<Contract> contracts;
	public ArrayList<Route> routes;	
	public String groups;
	public String username;
	public String nonce;
	
	

	public RequestContext(HttpServletRequest request, HttpServletResponse response) {
	    receivedTimestampMs = System.currentTimeMillis();		
	    this.request = request;
	    this.response = response;
	    port = request.getLocalPort();
		ssl = request.getScheme().equals("https") ;//?? (port == sslPort);		
		gateway = request.getServerName();
		consumer_ip = request.getRemoteAddr();
    	if (request.getHeader("Host") != null) {	
    		gateway_host = request.getHeader("Host").split(":",2)[0];
    	}
    	if (request.getHeader("X-forwarded-for") != null) {
			consumer_ip  += "," + request.getHeader("X-forwarded-for");							
		}   
    	
    	if (request.getHeader("X-forwarded-host") != null) {			
			gateway_host = request.getHeader("X-forwarded-host");					
		}    
    	method = request.getMethod();
    	URI = request.getRequestURI();
		QUERY_STRING = request.getQueryString();
		if (QUERY_STRING == null) QUERY_STRING = ""; 
		else if (QUERY_STRING.length()>0) {
    		QUERY_STRING = "?" + QUERY_STRING;
    	}    	
    	params = new HashMap<String,String>();
    	if (QUERY_STRING.length()>1) {
			for(String nv:QUERY_STRING.substring(1).split("\\&")) {
				String[] nv2 = nv.split("\\=",2);
	    		String value = "";
	    		if (nv2.length==2)  {
		    		try {
		    			if (nv2.length==2) value = URLDecoder.decode(nv2[1],"UTF-8");
		    		} catch (UnsupportedEncodingException e) {
		    			value = nv2[1];
		    		}
	    		}
	    		params.put(nv2[0], value);
	    	}
    	}    	
    
	}
}
