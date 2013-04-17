package co.gridport.server.kafka;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilderException;
import javax.ws.rs.core.UriInfo;

import co.gridport.server.GenericResource;

@Path("")
public class HelpResource  extends GenericResource {

    @Context UriInfo uriInfo;

    @GET
    @Path("")
    @Produces(MediaType.TEXT_HTML)
    public Response getHelp() throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        put("baseUri", uriInfo.getBaseUriBuilder().path(HelpResource.class).build());
        return view("kafka/help.vm");
    }

    @POST
    @Path("")
    public Response getClusterHelp(@FormParam("zk") String zkServer) {
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(ClusterInfoResource.class).build(zkServer)).build();
    }
}
