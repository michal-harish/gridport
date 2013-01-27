package co.gridport.server.handler;

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.ClientThread;
import co.gridport.server.ClientThreadManager;
import co.gridport.server.ClientThreadRouter;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.jms.ClientThreadJMS;
import co.gridport.server.space.ClientThreadSpace;

public class RequestHandler extends AbstractHandler {
	static private Logger log = LoggerFactory.getLogger("request");	
	
	static public ArrayList<ClientThread> threads = new ArrayList<ClientThread>();
    
    public void handle(String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response) 
        throws IOException, ServletException
    {
    	RequestContext context = (RequestContext) request.getAttribute("context");    	

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
    	
    	synchronized(threads) {
    	    thread.setName("GridPort Client Request");
    		threads.add(thread);    		        	    
    	}

    	thread.start();    	
    	try {
            thread.join(); //TODO investigate the SelectChannel connector thread context
            baseRequest.setHandled(true);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
    }
    
}

