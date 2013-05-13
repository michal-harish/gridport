package co.gridport.server.handler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import joptsimple.internal.Strings;

import org.apache.log4j.lf5.util.StreamUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.config.ConfigProvider;
import co.gridport.server.domain.Contract;
import co.gridport.server.domain.Endpoint;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;

public class Firewall extends AbstractHandler
{
    static private Logger log = LoggerFactory.getLogger("firewall");
    private ConfigProvider config;
    
    public Firewall(ConfigProvider config) {
        this.config = config;
    }

    public void handle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response) 
        throws IOException, ServletException
    {
        if (request.getRequestURI().equals("/favicon.ico"))
        {
            response.setStatus(200);
            StreamUtils.copy(ClassLoader.class.getResourceAsStream("/favicon.ico"), response.getOutputStream());
            baseRequest.setHandled(true);
            return; 
        }
        baseRequest.setAttribute("status", "Initializing");
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
        Collection<Contract> definedContracts = config.getContracts();
        ArrayList<Contract> result = new ArrayList<Contract>();
        for(Contract C: definedContracts) {
            boolean within = (C.getIpFilters().isEmpty());
            if (!within) {
                for(String range:C.getIpFilters()) {
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
                            if (IP.trim().length()>=range.length() && IP.trim().substring(0,range.length()).equals(range)) {
                                log.trace("available contract "+C.getName()+", EXACT MATCH="+IP);
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
            for(Contract C: availableContracts) {
                if (C.getEndpoints().size() > 0) {
                    if (!C.hasEndpoint(route.ID)) continue;
                }
                route.contracts.add(C);
                if (C.getGroups().size() == 0) route.defaultRoute = true;
                log.debug("available contract "+C.getName() +" + route "+route.endpoint);
            }
            if (route.contracts.size()==0) {
                log.debug("route without contract: "+route.endpoint);
                routes.remove(route);
                if (routes.size()==0) break;
            }
        }
        return routes; 
    }

    public List<Route> filterEndpointsByRequest(RequestContext context) {

        List<Endpoint> endpoints = new ArrayList<Endpoint>();
        for(Endpoint e: config.getEndpoints().values()) {
            if (!Strings.isNullOrEmpty(e.getGateway()) && !e.getGateway().equals(context.getGateway())) continue;
            if (!Strings.isNullOrEmpty(e.getGatewayHost()) && !e.getGatewayHost().equals(context.getHost())) continue;
            if (!Strings.isNullOrEmpty(e.getHttpMethod()) && !e.getHttpMethod().contains(context.getMethod())) continue;
            if (e.getSsl()!=null && !e.getSsl().equals(context.isHttps())) continue;
            endpoints.add(e);
        }

        String URI = context.getURI();

        List<Route> result = new ArrayList<Route>();
        boolean removeAllWildCards = false;
        ArrayList<Route> wildCards =new ArrayList<Route>();
        String longestWildCard = "";
        for (Endpoint e: endpoints) {
            String base  = "";  
            if (!Strings.isNullOrEmpty(e.getUriBase())) {
                base = e.getUriBase();
                if (Strings.isNullOrEmpty(URI)) {
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

            //Glue base uri and context uri with a slash - this is not a trailing slash
            String targetUri = URI.substring(base.length());
            targetUri = targetUri.replaceFirst("^([^/]{1})","/$1");

            // route has passed the uri filter
            Route route = new Route(
                    e.isWildcard(),
                    e.getId(),
                    e.getGateway(),
                    context.getMethod(), //e.getHttpMethod(),
                    context.getHost(), //e.getGatewayHost(),
                    e.getAsync(),
                    targetUri,
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
