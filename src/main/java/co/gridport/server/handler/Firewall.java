package co.gridport.server.handler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.GridPortServer;
import co.gridport.server.domain.Contract;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;
import co.gridport.server.utils.Utils;

public class Firewall extends AbstractHandler
{
    static private Logger log = LoggerFactory.getLogger("firewall");
    static private List<Contract> definedContracts;
    static private List<Route> definedRoutes;     
    
    public void handle(
        String target,
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
        
        //Check available contracts first and reject if none available
        if (definedContracts == null) loadDefinedContracts();
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
        ArrayList<Route> availableRoutes = filterRoutesByRequest(context, availableContracts);
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

    private void loadDefinedContracts() {
        definedContracts = new ArrayList<Contract>();
        try {
            Statement sql = GridPortServer.policydb.createStatement();
            try {
                ResultSet rs = sql.executeQuery("SELECT * FROM contracts");
                while (rs.next()) {
                    String name = rs.getString("name") == null ? "default" : rs.getString("name");
                    synchronized(definedContracts) {
                        ArrayList<String> groups = new ArrayList<String>();
                        if (rs.getString("auth_group") != null && !rs.getString("auth_group").trim().equals("")) {
                            
                            for(String s:rs.getString("auth_group").trim().split("[\\s\\n\\r,]+")) {
                                if (!Utils.blank(s.trim())) groups.add(s.trim());
                            }                                
                        }
                        ArrayList<String> endpoints = new ArrayList<String>();
                        if (rs.getString("content") != null  && !rs.getString("content").trim().equals("")) {                                
                            for(String s:rs.getString("content").trim().split("[\\s\\n\\r,]+")) {
                                if (!Utils.blank(s.trim())) endpoints.add(s.trim());                                    
                            }                                
                        }                            
                        definedContracts.add(new Contract(
                            name,
                            rs.getString("ip_range"),
                            new Long(Math.round(rs.getFloat("interval") * 1000)),
                            rs.getLong("frequency"),
                            groups.toArray( new String[groups.size()]),
                            endpoints.toArray( new String[endpoints.size()])
                            
                        ));
                    }
                }
            } finally {
                sql.close();
            }  
        } catch (SQLException e) {          
            log.error("Contract SQL error",e);  
        }
    }

    private ArrayList<Contract> filterContractsByIP(String[] remoteIP) {
        
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
                        //TODO wildcard matching                                
                        for(String IP:remoteIP) if (IP.trim().equals(range)) {
                            log.debug("available contract "+C.getName()+", EXACT MATCH="+IP);
                            within = true;  
                            break;
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

    private ArrayList<Route> filterRoutesByRequest(RequestContext context, List<Contract> availableContracts) {
        ArrayList<Route> routes = new ArrayList<Route>();
        
        Statement sql;
        String qry = "SELECT * FROM endpoints" +
            " WHERE (gateway IS NULL OR gateway='' OR gateway='"+context.getGateway()+"')" +
            " AND (gateway_host IS NULL OR gateway_host='' OR gateway_host='"+context.getHost()+"')" +       
            " AND (http_method IS NULL OR http_method='' OR http_method LIKE '%"+context.getMethod()+"%')" +
            " AND (ssl='"+(context.isHttps() ? "1" : "0")+"' OR ssl IS NULL OR ssl='')";                              
        try {
            sql = GridPortServer.policydb.createStatement();            
            log.debug("REQUEST ROUTING: host="+context.getHost()+", method="+context.getMethod()+", ssl="+(context.isHttps() ? "1" : "0")+", request="+context.getURI()+context.getQueryString());
            try {   
                ResultSet rs = sql.executeQuery(qry);
                routes = filterRoutes(rs, context.getURI(), context.getQueryString(), context.isHttps());

                for(Route E:routes) {   
                    E.defaultRoute = false;
                    //remove routes with unmatching gateway_host
                    if (!Utils.blank(E.gateway_host)) {                                 
                        if (!context.getHost().equals(E.gateway_host)) {
                            routes.remove(E);
                            if (routes.size()==0) break; else continue;
                        }
                    }
                    //remove route if it doesn't have available contract
                    boolean hasContract = false;
                    for(Contract C: availableContracts) {                     
                        if (!C.hasEndpoint(E.ID)) continue;                                             
                        //if (!C.hasEitherGroup(E.auth_group)) continue;
                        E.contracts.add(C);
                        if (C.getAuthGroup().length ==0) E.defaultRoute = true;
                        log.debug("available contract "+C.getName());
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
