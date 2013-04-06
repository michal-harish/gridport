package co.gridport.server.manager;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import co.gridport.GridPortServer;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.handler.ProxyHandler;
import co.gridport.server.space.Space2;
import co.gridport.server.space.Subscription;

@Path("/")
public class RootResource extends Resource {

    @Context UriInfo uriInfo;
    @Context HttpServletRequest request;

    @GET
    @Path("")
    public Response index() 
            throws IllegalArgumentException, SecurityException, NoSuchMethodException 
    {
        RequestContext context = (RequestContext) request.getAttribute("context");
        put("processList", getProcessList());
        put("userList", GridPortServer.policyProvider.getUsers());
        put("user", context.getUsername());// equals(obj)request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName());
        return view("manager/home/index.vm");
    }

    @GET
    @Path("/hello/{param}")
    public Response printMessage(@PathParam("param") String msg) {
        put("msg", msg);
        return view("manager/home/hello.vm"); 
    }

    public String getPrintLink(String arg) throws IllegalArgumentException, SecurityException, NoSuchMethodException {
        UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder().path(this.getClass().getMethod("printMessage",String.class));
        return uriBuilder.build(arg).toString();
    }

    public String[] getProcessList() {
        List<String> processList = new ArrayList<String>();

        for(String threadInfo: ProxyHandler.getActiveThreadsInfo()) {
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
