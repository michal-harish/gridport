package co.gridport.server.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import co.gridport.server.domain.Endpoint;

@Path("/endpoints")
public class EndpointsResource extends Resource {

    @Context public HttpServletRequest request;

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<Integer,Endpoint> getEndpoints() {
        return GridPortServer.policyProvider.getEndpoints();
    }

    @POST
    @Path("")
    @Produces(MediaType.TEXT_HTML)
    public Response addEndpoint() 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        try {
            Endpoint endpoint = GridPortServer.policyProvider.newEndpoint();
            return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getEndpoint",Integer.class)).build(endpoint.getId())).build();
        } catch (Exception e) { 
            e.printStackTrace();
            return Response.seeOther(uriInfo.getBaseUriBuilder().path(HomeResource.class).path(HomeResource.class.getMethod("index",String.class)).queryParam("msg", e.getMessage()).build()).build();
        }
    }


    @GET
    @Path("/{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response getEndpoint(@PathParam("id") Integer id) {
        Endpoint endpoint = GridPortServer.policyProvider.getEndpoints().get(id);
        List<Contract> contracts = new ArrayList<Contract>();
        for(Contract c: GridPortServer.policyProvider.getContracts()) {
            if (c.hasEndpoint(id)) {
                contracts.add(c);
            }
        }

        put("endpoint", endpoint);
        put("contracts", contracts);
        return view("manage/endpoint.vm");
    }

    @POST
    @Path("/{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response updateEndpoint(
        @PathParam("id") Integer id,
        @FormParam("protocol") Integer protocol,
        @FormParam("gateway") String gateway,
        @FormParam("gatewayHost") String gatewayHost,
        @FormParam("httpMethod") String httpMethod,
        @FormParam("uriBase") String uriBase,
        @FormParam("endpoint") String endpoint,
        @FormParam("async") String async
    ) throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {

        GridPortServer.policyProvider.updateEndpoint(new Endpoint(
            id,
            protocol == 0 ? null : protocol == 1 ? true : false,
            gateway,
            gatewayHost,
            httpMethod,
            uriBase,
            endpoint,
            async
        ));
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getEndpoint",Integer.class)).build(id)).build();

    }
}
