package co.gridport.server.jms;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import co.gridport.server.VelocityResource;

@Path("/")
public class HelpResource extends VelocityResource {

    @GET
    @Path("")
    @Produces(MediaType.TEXT_HTML)
    public Response getHelp() {
        return view("jms/help.vm");
    }
}
