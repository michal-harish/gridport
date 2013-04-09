package co.gridport.server.domain;

import co.gridport.server.utils.Utils;


public class Endpoint {

    private Integer id;
    private Boolean ssl;
    private String gateway;
    private String gatewayHost;
    private String httpMethod;
    private String uriBase;
    private String endpoint;
    private String async;
    
    public Endpoint(
        Integer id,
        Boolean ssl,
        String gateway,
        String gatewayHost,
        String httpMethod,
        String uriBase,
        String endpoint,
        String async
    ) {
        this.id = id;
        this.ssl = ssl;
        this.gateway = gateway;
        this.gatewayHost = gatewayHost == null ? "" : gatewayHost;
        this.httpMethod = httpMethod;
        this.uriBase = uriBase;
        this.endpoint = endpoint;
        this.async = async;
    }

    public Integer getId() {
        return id;
    }

    public Boolean getSsl() {
        return ssl;
    }

    public void setSsl(Boolean ssl) {
        this.ssl = ssl;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getGatewayHost() {
        return gatewayHost;
    }

    public String getUrlPattern(String ServerName) {
        return 
            (ssl == null ? "http(s)" : ssl ? "https" : "http" ) +"://"
            + (!Utils.blank(gatewayHost) ? gatewayHost : ServerName )
            + (!Utils.blank(uriBase) ? uriBase : "/*" )
            ;
    }

    public void setGatewayHost(String gatewayHost) {
        this.gatewayHost = gatewayHost;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getUriBase() {
        return uriBase;
    }

    public void setUriBase(String uriBase) {
        this.uriBase = uriBase;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Boolean isWildcard() {
        if (Utils.blank(uriBase)) {
            return true;
        } else if (uriBase.substring(uriBase.length()-1).equals("*")) { 
            return true;
        }
        return false;
    }

    public String getAsync() {
        return async;
    }

    public void setAsync(String async) {
        this.async = async;
    }

}
