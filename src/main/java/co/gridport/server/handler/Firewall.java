package co.gridport.server.handler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.lf5.util.StreamUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.GridPortServer;
import co.gridport.server.domain.Contract;
import co.gridport.server.domain.Endpoint;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;
import co.gridport.server.utils.Utils;

public class Firewall extends AbstractHandler
{
    static private Logger log = LoggerFactory.getLogger("firewall");
    
    public void handle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response) 
        throws IOException, ServletException
    {

        if (request.getRequestURI().equals("/")) {
            response.sendRedirect("/manage");
            baseRequest.setHandled(true);
            return;
        }
        if (request.getRequestURI().equals("/favicon.ico"))
        {
            response.setStatus(200);
            StreamUtils.copy(ClassLoader.class.getResourceAsStream("/favicon.ico"), response.getOutputStream());
            baseRequest.setHandled(true);
            return; 
        }

        //Check available contracts first and reject if none available
        String remoteIP = request.getRemoteAddr();
        if (request.getHeader("X-forwarded-for") != null) {
            remoteIP  += "," + request.getHeader("X-forwarded-for");
        }
        ArrayList<Contract> availableContracts = filterContractsByIP(remoteIP.split(","));
        if (availableContracts.size()==0) {
            log.debug("Firewall reject(403) " + baseRequest.getRequestURI());
            log.debug("no contract available for authenticator");
            response.setStatus(403);
            baseRequest.setHandled(true);
            return; 
        }
        RequestContext context = new RequestContext(request, response);

        //Check available routes and reject if none available
        List<Route> availableRoutes = routeByRequest(context, availableContracts);
        if (availableRoutes.size() ==0) {
            log.debug("no routes available for authenticator");
            response.setStatus(404);
            baseRequest.setHandled(true);
            return;
        }
        context.setRoutes(availableRoutes);

        //Passed both contract and route availability
        request.setAttribute("context", context);
    }

    private ArrayList<Contract> filterContractsByIP(String[] remoteIP) {
        List<Contract> definedContracts = GridPortServer.policyProvider.getContracts();
        ArrayList<Contract> result = new ArrayList<Contract>();
        for(Contract C: definedContracts) {
            boolean within = (C.getIpRange() == null || C.getIpRange().isEmpty());
            if (!within) {
                for(String range:C.getIpRange().split("[,\n\r]")) {
                    if (range.contains("-")) {
                        String[] r = range.split("-",2);
                        try {
                            byte[] ipLower = InetAddress.getByName(r[0].trim()).getAddress();
                            byte[] ipUpper = InetAddress.getByName(r[1].trim()).getAddress();
                            for(String ip:remoteIP) {
                                byte[] ipRemote = InetAddress.getByName(ip).getAddress();
                                boolean w = true;
                                for(int i=0;i< (Math.min(ipLower.length,ipRemote.length));i++) {
                                    int lb = ipLower[i] < 0 ? 256+ ipLower[i] : ipLower[i];
                                    int ub = ipUpper[i] < 0 ? 256+ ipUpper[i] : ipUpper[i];
                                    int rb = ipRemote[i] < 0 ? 256+ ipRemote[i] : ipRemote[i];
                                    if (rb>=lb && rb<=ub) continue;
                                    w = false;
                                }
                                if (w) {
                                    within = true;
                                    log.debug("available contract "+C.getName()+", IN RANGE="+range+" ");
                                    break;
                                }
                            }
                        } catch(UnknownHostException e) {
                            log.warn("Contract - unknown address ",e);
                            continue;
                        }
                    } else {
                        for(String IP:remoteIP) {
                            if (IP.trim().substring(0,range.length()).equals(range)) {
                                log.debug("available contract "+C.getName()+", EXACT MATCH="+IP);
                                within = true;  
                                break;
                            }
                        }
                    }
                    if (within) break; 
                }
            }
            if (within) {
                result.add(C);
            }
        } 

        return result;
    }

    private List<Route> routeByRequest(RequestContext context, List<Contract> availableContracts) {

        log.debug("REQUEST ROUTING: host="+context.getHost()+", method="+context.getMethod()+", ssl="+(context.isHttps() ? "1" : "0")+", request="+context.getURI()+context.getQueryString());

        //reduce defined endpoints to available routes
        List<Route> routes = filterEndpointsByRequest(context);

        //filter routes by available contracts
        for(Route route:routes) {
            route.defaultRoute = false;
            //remove route if it doesn't have available contract
            boolean hasContract = false;
            for(Contract C: availableContracts) {
                if (!C.hasEndpoint(route.ID)) continue;
                //if (!C.hasEitherGroup(E.auth_group)) continue;
                route.contracts.add(C);
                if (C.getGroups().size() == 0) route.defaultRoute = true;
                log.debug("available contract "+C.getName());
                hasContract = true;
            }
            if (!hasContract) {
                routes.remove(route);
                if (routes.size()==0) break;
            }
        }
        return routes; 
    }

    private static List<Route> filterEndpointsByRequest(RequestContext context) {

        List<Endpoint> endpoints = new ArrayList<Endpoint>();
        for(Endpoint e: GridPortServer.policyProvider.getEndpoints()) {
            if (!Utils.blank(e.getGateway()) && !e.getGateway().equals(context.getGateway())) continue;
            if (!Utils.blank(e.getGatewayHost()) && !e.getGatewayHost().equals(context.getHost())) continue;
            if (!Utils.blank(e.getHttpMethod()) && !e.getHttpMethod().contains(context.getMethod())) continue;
            if (e.getSsl()!=null && !e.getSsl().equals(context.isHttps())) continue;
            endpoints.add(e);
        }

        String URI = context.getURI();
        if (!URI.matches("^.*?\\.[^/]+$")) URI += "/";

        List<Route> result = new ArrayList<Route>();
        boolean removeAllWildCards = false;
        ArrayList<Route> wildCards =new ArrayList<Route>();
        String longestWildCard = "";
        for (Endpoint e: endpoints) {
            String base  = "";  
            if (!Utils.blank(e.getUriBase())) {
                base = e.getUriBase();
                if (Utils.blank(URI)) {
                    // empty query cannot match where uri_base is set
                    continue;
                }
                if (base.substring(base.length()-1).equals("*")) { 
                    //wildcard can match the query from right
                    base = base.substring(0,base.length()-1);
                }
                if (URI.length()<base.length()) {
                    //query shorter than uri_base won't match either
                    continue;
                } else if (e.isWildcard()) { 
                    //wildcard must match the query from right
                    if (!URI.substring(0,base.length()).equals(base)) { 
                        continue; 
                    } 
                    else
                    {
                        //ok - wildcard match
                        //keep only the most specific wild cards
                        if (longestWildCard.length()<e.getUriBase().length()) {
                            longestWildCard = e.getUriBase();
                        }
                    }
                } else if (!URI.equals(base)) { 
                    continue; 
                } else {
                    //ohterwise the uri_base must be an exact match
                    removeAllWildCards = true;
                }
            }

            // route has passed the uri filter
            Route route = new Route(
                    e.isWildcard(),
                    e.getId(),
                    e.getGateway(),
                    context.getMethod(), //e.getHttpMethod(),
                    context.getHost(), //e.getGatewayHost(),
                    e.getAsync(),
                    URI.substring(base.length()).replaceFirst("^([^/]{1})","/$1"), //target URI
                    e.getEndpoint().replaceFirst("/$",""),
                    context.getQueryString(),
                    e.getUriBase() == null ? "" : e.getUriBase(),
                    base
                );
            result.add(route);
            if (e.isWildcard()) 
            {
                wildCards.add(route);
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
