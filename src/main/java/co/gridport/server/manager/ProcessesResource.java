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

import co.gridport.server.GridPortServer;
import co.gridport.server.domain.RequestContext;

@Path("/requests")
public class ProcessesResource extends Resource {

    @Context public HttpServletRequest request;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getActiveRequests() {

        List<RequestContext> requests = new ArrayList<RequestContext>();
        for(Request r: Collections.unmodifiableList(GridPortServer.getActiveRequests())) {
            requests.add((RequestContext)r.getAttribute("context"));
        }
        put("requests", requests);
        /*
        getActiveThreadsInfo() {
            List<String> result = new ArrayList<String>();
            synchronized (threads) {
                for(ClientThread T:threads) { 
                    result.add (T.getInfo());
                    if (T instanceof ClientThreadRouter) {
                        ClientThreadRouter TR = (ClientThreadRouter) T;
                        synchronized (TR.getAsyncSubrequests()) 
                        {
                            for(SubRequest S:TR.getAsyncSubrequests()) {
                                result.add(" + EVENT " + S.getRequestMethod() + " " +S.getURL() + " ; "+ (S.error!=null ? S.error : "") +"\n");
                            }
                        }
                    }
                }
            }
            return result;
        }

        synchronized(Space2.subs) {
            for(Subscription T:Space2.subs) {   
                processList.add("SPACE SUBSCRIPTION " + T.pattern + " TO " + T.target + " "+ ((System.currentTimeMillis() - T.started )/1000) + "sec) \n");
            }
        }
         */
        return view("manage/requests.vm");
    }
}
