package co.gridport.server.manager;

import java.net.URI;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import co.gridport.GridPortServer;

@Path("/server")
public class ServerResource extends Resource
{
    @Context UriInfo uriInfo;

    @GET
    @Path("/")
    public Response index() {
        return view("manage/server.vm");
    }

    @POST
    @Path("/")
    public Response doRestart(@FormParam("restart") String restart ) {
        GridPortServer.restart();
        URI uri = uriInfo.getBaseUriBuilder().path(RootResource.class).build();
        return Response.ok().header("Refresh", "2;url="+uri.toString()).build();
        //return Response.seeOther(uri).build();
    }
}
