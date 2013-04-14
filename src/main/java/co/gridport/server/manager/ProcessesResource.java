package co.gridport.server.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.jetty.server.Request;

import co.gridport.GridPortServer;
import co.gridport.server.domain.RequestContext;

@Path("/requests")
public class ProcessesResource extends Resource {

    @Context public HttpServletRequest request;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getActiveRequests() {
        List<RequestContext> requests = new ArrayList<RequestContext>();
        for(Request r: Collections.unmodifiableList(GridPortServer.activeRequests)) {
            requests.add((RequestContext)r.getAttribute("context"));
        }
        put("requests", requests);
        return view("manage/requests.vm");
    }
}
