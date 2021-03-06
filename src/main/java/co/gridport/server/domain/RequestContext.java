package co.gridport.server.domain;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;

public class RequestContext {

    private Long receivedTimestampMs;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private String host;
    private String consumerAddr;
    private String queryString;
    private HashMap<String,String> params;
    private List<Route> routes;
    private List<String> groups;
    private String username;
    private String sessionToken;

    private Contract preferredContract;
    private Long waitingTime;

    public RequestContext(
        HttpServletRequest request, 
        HttpServletResponse response
    ) {
        receivedTimestampMs = System.currentTimeMillis();

        this.request = request;
        this.response = response;

        if (request.getHeader("Host") != null) {
            host = request.getHeader("Host").split(":",2)[0];
        }
        if (request.getHeader("X-forwarded-host") != null) {
            host = request.getHeader("X-forwarded-host");
        }

        consumerAddr = request.getRemoteAddr();
        if (request.getHeader("X-forwarded-for") != null) {
            consumerAddr  += "," + request.getHeader("X-forwarded-for");
        }

        params = new HashMap<String,String>();
        if (request.getQueryString()!=null) {
            for(String nv:request.getQueryString().split("\\&")) {
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

        queryString = request.getQueryString();
        if (queryString == null) queryString = ""; 
        else if (queryString.length()>0) {
            queryString = "?" + queryString;
        }
    }

    public void setRoutes(List<Route> availableRoutes) {
        routes = availableRoutes;
    }

    public List<Route> getRoutes() {
        return routes;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }
    public List<String> getGroups() {
        return groups;
    }
    public String getRealm() {
        return StringUtils.join(groups,",");
    }

    public void setUsername(String username) {
        this.username = username;
    }
    public String getUsername() {
        return username;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public boolean isHttps() {
        return request.getScheme().equals("https");
    }

    public String getMethod() {
        return request.getMethod();
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    public Long getReceivedTimestampMs() {
        return receivedTimestampMs;
    }

    public String getGateway() {
        return request.getServerName();
    }

    public String getHost() {
        return host;
    }

    public String getURI() {
        return request.getRequestURI();
    }

    public String getConsumerAddr() {
        return consumerAddr;
    }

    public String getQueryString() {
        return queryString;
    }

    public String getStatus() {
        return (String)request.getAttribute("status");
    }

    public void setPreferredContract(Contract contract) {
        this.preferredContract = contract;
    }

    public Contract getPreferredContract() {
        return preferredContract;
    }

    public Long getWaitingTime() {
        return waitingTime;
    }

    public void setWaitingTime(Long waitingTime) {
        this.waitingTime = waitingTime;  
    }

}
