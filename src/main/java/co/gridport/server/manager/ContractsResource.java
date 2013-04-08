package co.gridport.server.manager;

import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilderException;

import co.gridport.GridPortServer;
import co.gridport.server.domain.Contract;

@Path("/contracts")
public class ContractsResource extends Resource{

    @Context HttpServletRequest request;

    @Path("/{name}")
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getContract(@PathParam("name") String name)
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        put("contract", GridPortServer.policyProvider.getContract(name));
        put("endpoints", GridPortServer.policyProvider.getEndpoints());
        put("groupsUri", uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getGroups",String.class)).build(name));
        put("ipFiltersUri", uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getIpFilters",String.class)).build(name));
        put("endpointsUri", uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getEndpoints",String.class)).build(name));
        return view("manage/contract.vm");
    }

    @GET
    @Path("/{name}/groups")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getGroups(@PathParam("name") String name) {
        return GridPortServer.policyProvider.getContract(name).getGroups();
    }

    @GET
    @Path("/{name}/ipfilters")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getIpFilters(@PathParam("name") String name) {
        return GridPortServer.policyProvider.getContract(name).getIpFilters();
    }

    @GET
    @Path("/{name}/endpoints")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Integer> getEndpoints(@PathParam("name") String name) {
        return GridPortServer.policyProvider.getContract(name).getEndpoints();
    }

    @POST
    @Path("/{name}/endpoints")
    @Produces(MediaType.TEXT_HTML)
    public Response updateAvailableEndpoints(@PathParam("name") String name, @FormParam("endpoint") Integer[] endpoints) 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        Contract contract = GridPortServer.policyProvider.getContract(name);
        contract.setEndpoints(Arrays.asList(endpoints));
        GridPortServer.policyProvider.updateContract(contract);
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getContract",String.class)).build(name)).build();
    }
}
