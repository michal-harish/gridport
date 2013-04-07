package co.gridport.server.manager;

import java.io.File;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/logs")
public class LogsResource extends Resource {

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_PLAIN)
    public Response index()
    {
        return Response.ok().entity(new File("./logs/gridport.log")).build();
    }
}
