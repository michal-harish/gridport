package co.gridport.server.handler;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.ConfigProvider;
import co.gridport.server.GenericHandler;
import co.gridport.server.domain.Contract;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;

public class LoadBalancer extends GenericHandler
{
    static private Logger log = LoggerFactory.getLogger("loadbalancer");

    //private ConfigProvider config;

    public LoadBalancer(ConfigProvider config) {
        //this.config = config;
    }

    public void handle(String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response) 
        throws IOException, ServletException
    {
        RequestContext context = (RequestContext) request.getAttribute("context");
        baseRequest.setAttribute("status", "Load-Balancing");

        Contract contract = null;
        List<String> groups = context.getGroups();
        for(Route E:context.getRoutes()) {   
            for(Contract C:E.contracts) synchronized(C) {
                String found = null;
                if (C.getGroups().size()>0) { // contract is only for some auth groups
                    for(String gC:C.getGroups()) {
                        for(String g:groups) if (g.trim().equals(gC.trim())) {
                            found = g.trim(); 
                            break;
                        }
                        if (found!=null) break;
                    }
                    if (found==null) continue;
                }
                if (contract == null) { 
                    contract = C;
                } else if (C.getIntervalMs()<contract.getIntervalMs()) {
                    contract = C;
                } else if (C.getFrequency()>contract.getFrequency()) {
                    contract = C;
                }
            }
        }
        if (contract == null) {
            serveHtmlError(response, 403, "Forbidden", "No contract available");
            baseRequest.setAttribute("status", "Rejected");
            baseRequest.setHandled(true);
            return;
        } 

        baseRequest.setAttribute("status", "Serving");
        log.debug("Selected contract:"+ contract.getName() + " for user:" + context.getUsername());
        context.setWaitingTime(contract.consume());
        context.setPreferredContract(contract);
    }

}
