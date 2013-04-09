package co.gridport.server.manager;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import co.gridport.GridPortServer;

@Path("/endpoints")
public class EndpointsResource extends Resource {

    @Context HttpServletRequest request;

    @GET
    @Path("/{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response getEndpoint(@PathParam("id") Integer id) {
        put("endpoint", GridPortServer.policyProvider.getEndpoints().get(id));
        return view("manage/endpoint.vm");
    }
}
