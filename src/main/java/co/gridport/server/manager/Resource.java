package co.gridport.server.manager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriBuilderException;
import javax.ws.rs.core.UriInfo;

import co.gridport.server.VelocityResource;
import co.gridport.server.domain.Contract;
import co.gridport.server.domain.RequestContext;

public abstract class Resource extends VelocityResource {

    @Context public UriInfo uriInfo;
    @Context public HttpServletRequest request;
    @Context public HttpServletResponse response;


    public String getHomeUrl() {
        return uriInfo.getBaseUriBuilder().path(HomeResource.class,"index").build().toString();
    }
    public String getLogsUrl() throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        return uriInfo.getBaseUriBuilder().path(LogsResource.class).path(LogsResource.class.getMethod("index")).build().toString();
    }
    public String getRestartUrl() throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        return uriInfo.getBaseUriBuilder().path(HomeResource.class).path(HomeResource.class.getMethod("restart")).build().toString();
    }
    public String getUsersUrl() {
        return uriInfo.getBaseUriBuilder().path(UsersResource.class).build().toString();
    }
    public String getContractsUrl() 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        return uriInfo.getBaseUriBuilder().path(ContractsResource.class).build().toString();
    }
    public String getContractUrl(Contract contract) 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        return uriInfo.getBaseUriBuilder().path(ContractsResource.class).path(ContractsResource.class.getMethod("getContract", String.class)).build(contract.getName()).toString();
    }
    public String getCurrentUser() {
        RequestContext context = (RequestContext) request.getAttribute("context");
        return context.getUsername();
    }
    public String getEndpointsUrl() 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        return uriInfo.getBaseUriBuilder().path(EndpointsResource.class).build().toString();
    }
    public String getEndpointUrl(Integer id) 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        return uriInfo.getBaseUriBuilder().path(EndpointsResource.class).path(EndpointsResource.class.getMethod("getEndpoint", Integer.class)).build(id).toString();
    }

}
