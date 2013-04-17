package co.gridport.server.jms;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import co.gridport.server.GenericResource;

@Path("/")
public class HelpResource extends GenericResource {

    @GET
    @Path("")
    @Produces(MediaType.TEXT_HTML)
    public Response getHelp() {
        return view("jms/help.vm");
    }
}
