package co.gridport.server;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.GridPortServer;
import co.gridport.server.jms.ClientThreadJMS;
import co.gridport.server.space.ClientThreadSpace;
import co.gridport.server.utils.Utils;

public class RequestHandler extends AbstractHandler {
	static private Logger log = LoggerFactory.getLogger("request");	
	static protected ArrayList<String> sessions = new ArrayList<String>();	
	static public ArrayList<ClientThread> threads = new ArrayList<ClientThread>();
    
    public void handle(String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response) 
        throws IOException, ServletException
    {
        //Firewall handler has passed at this moment        
        //Authenticator handler has passed too and the identity is in the context.username
    	GridPortContext context = (GridPortContext) request.getAttribute("context");
    	long received = context.received;
    	String afterAuth = String.valueOf( System.currentTimeMillis() - received);

    	//create Client Thread of appropriate type
		ClientThread thread;		
		if (context.routes.get(0).endpoint.equals("module://manager")) {
		    log.info("GridPortHandler -> ClientThreadManager " + request.getRequestURI());
    		thread = new ClientThreadManager(context);  

    	} else if (context.routes.get(0).endpoint.equals("module://space")) {    		
    	    log.info("GridPortHandler -> ClientThreadSpace " + request.getRequestURI());
    		thread = new ClientThreadSpace(context);

    	} else if (context.routes.get(0).endpoint.equals("module://jms")) {
    	    log.info("GridPortHandler -> ClientThreadJMS " + request.getRequestURI());
    		thread = new ClientThreadJMS(context);

    	} else {
    	    log.info("GridPortHandler -> ClientThreadRouter " + request.getRequestURI());
    		thread = new ClientThreadRouter(context);

    	}
		
		thread.context = context;   
    	thread.receivedMillisec = received;
    	thread.received = new Date(thread.receivedMillisec);
    	thread.setName("GridPort Client Request");
    	
    	synchronized(threads) {
    		threads.add(thread);    		        	    
    	}

    	thread.start();    
    	
    	long launch = System.currentTimeMillis() - thread.receivedMillisec;
    	thread.log(afterAuth+"/"+launch+"ms");
    	
    	try {
            thread.join(); //TODO investigate the SelectChannel connector thread context
            baseRequest.setHandled(true);
        } catch (InterruptedException e) {
            thread.log(e.getMessage());
        }
    }
    
    protected static ArrayList<Route> route(GridPortContext request) { // called from authenticator, returns required group, HEADERS NOT CONSIDERED HERE YET 
    	ArrayList<Route> routes = new ArrayList<Route>();
    	
		Statement sql;
        String qry = "SELECT * FROM endpoints" +
            " WHERE (gateway IS NULL OR gateway='' OR gateway='"+request.gateway+"')" +
            " AND (gateway_host IS NULL OR gateway_host='' OR gateway_host='"+request.gateway_host+"')" +       
            " AND (http_method IS NULL OR http_method='' OR http_method LIKE '%"+request.method+"%')" +
            " AND (ssl='"+(request.ssl ? "1" : "0")+"' OR ssl IS NULL OR ssl='')";                              
		try {
			sql = GridPortServer.policydb.createStatement();			
			log.debug("REQUEST ROUTING: host="+request.gateway_host+", method="+request.method+", ssl="+(request.ssl ? "1" : "0")+", request="+request.URI+request.QUERY_STRING);
            try {   
				ResultSet rs = sql.executeQuery(qry);
				routes = filterRoutes(rs,request.URI,request.QUERY_STRING,request.ssl);

				for(Route E:routes) {	
					E.defaultRoute = false;
					//remove routes with unmatching gateway_host
					if (!Utils.blank(E.gateway_host)) {									
		        		if (!request.gateway_host.equals(E.gateway_host)) {
		        			routes.remove(E);
		        			if (routes.size()==0) break; else continue;
		        		}
					}
	        		//remove route if it doesn't have available contract
	        		boolean hasContract = false;
	        		for(Contract C:request.contracts) {	        			
	        			if (!C.hasEndpoint(E.ID)) continue;	        				        			
	        			//if (!C.hasEitherGroup(E.auth_group)) continue;
        				E.contracts.add(C);
        				if (C.auth_group.length ==0) E.defaultRoute = true;
        				log.debug("available contract "+C.name);
        				hasContract = true;
	        			
	        		}
	        		if (!hasContract) {
	        			routes.remove(E);
	        			if (routes.size()==0) break; else continue;
	        		}
	        	}
			
			} finally {
				sql.close();
			}
		} catch (SQLException e) {			
			log.error("routing table sql error " + qry,e);			
		} catch (Exception e2) {
		    log.error("routing table error " + qry,e2);
		}
		return routes; 
    }
    
    private static ArrayList<Route> filterRoutes(ResultSet rs,String URI,String QUERY_STRING,boolean ssl) throws SQLException {
    		
    	if (!URI.matches("^.*?\\.[^/]+$")) URI += "/";    	  	
    	ArrayList<Route> result = new ArrayList<Route>();
    	boolean removeAllWildCards = false;
    	ArrayList<Route> wildCards =new ArrayList<Route>();
    	String longestWildCard = "";
    	while (rs.next()) {    		
    		String base  = "";	
    		Boolean wildcard = false;    		
        	if (Utils.blank(rs.getString("uri_base"))) {
           		//route with no uri_base is an implicit wildcard that always matches
           		wildcard = true;
        	} else {
        		base = rs.getString("uri_base");         
        		if (Utils.blank(URI)) {
        			// empty query cannot match where uri_base is set
        			continue;
        		}        		
        		if (base.substring(base.length()-1).equals("*")) { 
        			//wildcard can match the query from right        			
        			wildcard = true;        			
        			base = base.substring(0,base.length()-1);
        		}
        		if (URI.length()<base.length()) {
        			//query shorter than uri_base won't match either
        			continue;
    			} 
        		else if (wildcard) { 
        			//wildcard must match the query from right
        			if (!URI.substring(0,base.length()).equals(base)) { 
        				continue; 
        			} 
        			else
        			{
        				//ok - wildcard match
        				//keep only the most specific wild cards
        	        	if (longestWildCard.length()<rs.getString("uri_base").length()) {
        	        		longestWildCard = rs.getString("uri_base");
        	        	}
        			}        			
        		} 
        		else if (!URI.equals(base)) { 
        			continue; 
        		}
        		else
        		{
        			//ohterwise the uri_base must be an exact match
        			removeAllWildCards = true;
        		}
        	}
        	
        	// route has passed the uri filter
        	Route E = new Route(
        			wildcard,
        			rs.getString("ID"),
        			rs.getString("gateway"),
        			rs.getString("http_method"),
        			rs.getString("gateway_host"),
        			rs.getString("async"),
        			URI.substring(base.length()).replaceFirst("^([^/]{1})","/$1"), //target URI
        			rs.getString("service_endpoint").replaceFirst("/$",""),
        			QUERY_STRING,
        			rs.getString("uri_base") == null ? "" : rs.getString("uri_base"),
        			base
        		);
        	result.add(E);
        	if (wildcard) 
    		{
        		wildCards.add(E);
        	}
        }    	
    	for(Route E:wildCards) if (E.wildcard) {
    		if (removeAllWildCards
    		    || E.base_uri.length()<longestWildCard.length()) {    			
    			result.remove(E);
    		}
    	}
        return result;
    }

}

