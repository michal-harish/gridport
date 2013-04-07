package co.gridport.server.manager;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;
import javax.ws.rs.core.UriInfo;

import co.gridport.GridPortServer;
import co.gridport.server.domain.Contract;
import co.gridport.server.domain.Endpoint;
import co.gridport.server.domain.User;
import co.gridport.server.handler.RequestHandler;
import co.gridport.server.space.Space2;
import co.gridport.server.space.Subscription;

@Path("/")
public class HomeResource extends Resource {

    @Context UriInfo uriInfo;
    @Context HttpServletRequest request;


    @GET
    @Path("")
    public Response index() 
            throws IllegalArgumentException, SecurityException, NoSuchMethodException 
    {
        put("processes", getProcessList());
        put("endpoints", GridPortServer.policyProvider.getEndpoints());
        put("contracts", GridPortServer.policyProvider.getContracts());
        put("users", GridPortServer.policyProvider.getUsers());

        return view("manage/home/index.vm");
    }

    @GET
    @Path("/users.json")
    @Produces("application/json")
    public Collection<User> getUsers() {
        return GridPortServer.policyProvider.getUsers();
    }

    @GET
    @Path("/users/{username}")
    public Response userAccount(@PathParam("username") String username) {
        put("user", GridPortServer.policyProvider.getUser(username));
        return view("manage/users/account.vm");
    }

    @POST
    @Path("/users/{username}")
    public Response updateUserAccount(@PathParam("username") String username, @FormParam("password") String password) 
            throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException 
    {
        User user = GridPortServer.policyProvider.getUser(username);
        user.createPassport("", password);
        GridPortServer.policyProvider.updateUser(user);
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(HomeResource.class).build()).build();
    }

    @GET
    @Path("/contracts.json")
    @Produces("application/json")
    public List<Contract> getContracts() {
        return GridPortServer.policyProvider.getContracts();
    }

    @GET
    @Path("/endpoints.json")
    @Produces("application/json")
    public List<Endpoint> getEndpoints() {
        return GridPortServer.policyProvider.getEndpoints();
    }

    @GET
    @Path("/restart")
    public Response restart() {
        return view("manage/home/restart.vm");
    }
    @POST
    @Path("/restart")
    public Response doRestart(@FormParam("restart") String restart ) {
        GridPortServer.restart();
        URI uri = uriInfo.getBaseUriBuilder().path(HomeResource.class).build();
        return Response.ok().header("Refresh", "2;url="+uri.toString()).build();
    }

    public String getPrintLink(String arg) throws IllegalArgumentException, SecurityException, NoSuchMethodException {
        UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder().path(HomeResource.class.getMethod("printMessage",String.class));
        return uriBuilder.build(arg).toString();
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
