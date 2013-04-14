package co.gridport.server.manager;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilderException;
import javax.ws.rs.core.UriInfo;

import co.gridport.GridPortServer;
import co.gridport.server.ConfigProvider;
import co.gridport.server.handler.RequestHandler;
import co.gridport.server.space.Space2;
import co.gridport.server.space.Subscription;

@Path("/")
public class HomeResource extends Resource {

    @Context public UriInfo uriInfo;
    @Context public HttpServletRequest request;
    @Context public ConfigProvider config;


    @GET
    @Path("")
    @Produces(MediaType.TEXT_HTML)
    public Response index(@QueryParam("msg") @DefaultValue("") String msg) 
            throws IllegalArgumentException, SecurityException, NoSuchMethodException 
    {
        put("processes", getProcessList());
        put("endpoints", config.getEndpoints());
        put("contracts", config.getContracts());
        put("users", config.getUsers());
        put("msg", msg);

        return view("manage/index.vm");
    }

    @POST
    @Path("/restart")
    @Produces(MediaType.TEXT_HTML)
    public Response restart() throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        GridPortServer.restart();
        URI uri = uriInfo.getBaseUriBuilder().path(HomeResource.class).path(HomeResource.class.getMethod("index", String.class)).build();
        return Response.ok().header("Refresh", "2;url="+uri.toString()).build();
    }

    public String[] getProcessList() {
        List<String> processList = new ArrayList<String>();

        for(String threadInfo: RequestHandler.getActiveThreadsInfo()) {
            processList.add(threadInfo);
        }

        synchronized(Space2.subs) {
            for(Subscription T:Space2.subs) {   
                processList.add("SPACE SUBSCRIPTION " + T.pattern + " TO " + T.target + " "+ ((System.currentTimeMillis() - T.started )/1000) + "sec) \n");
            }
        }
        return processList.toArray(new String[processList.size()]);
    }

}
