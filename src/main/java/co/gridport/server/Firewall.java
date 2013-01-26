package co.gridport.server;

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Firewall extends AbstractHandler
{
    private static Logger log = LoggerFactory.getLogger("firewall");
    
    public void handle(String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response) 
        throws IOException, ServletException
    {        
        if (request.getRequestURI().equals("/favicon.ico"))
        {
            log.debug("Firewall ignore(404) " + baseRequest.getRequestURI());
            response.setStatus(404);
            baseRequest.setHandled(true);
            return; 
        }
        
        //select available contracts into the request
        ArrayList<Contract> availableContracts = Contract.getAvailableContracts(request);
        if (availableContracts.size()==0) {
            log.debug("Firewall reject(403) " + baseRequest.getRequestURI());
            log.debug("no contract available for authenticator");
            response.setStatus(403);
            baseRequest.setHandled(true);
            return; 
        }
        
        //passed - there are available contracts        
        GridPortContext context = new GridPortContext(request, response);        
        context.contracts = availableContracts;
        request.setAttribute("context", context);
    }
}
