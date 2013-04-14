package co.gridport.server.manager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilderException;

import co.gridport.server.ConfigProvider;
import co.gridport.server.domain.User;

@Path("/users")
public class UsersResource extends Resource {

    @Context public HttpServletRequest request;
    @Context public ConfigProvider config;

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<User> getUsers() {
        return Collections.unmodifiableCollection(config.getUsers());
    }

    @POST
    @Path("")
    @Produces(MediaType.TEXT_HTML)
    public Response addUser(@FormParam("username") String username) throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        try {
            if (config.getUser(username) != null) {
                throw new IllegalArgumentException("User `"+username+"` already exists");
            }
            User user = new User(username, "",null);
            config.updateUser(user);
            return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("userAccount",String.class)).build(username)).build();
        } catch (Exception e) { 
            e.printStackTrace();
            return Response.seeOther(uriInfo.getBaseUriBuilder().path(HomeResource.class).path(HomeResource.class.getMethod("index",String.class)).queryParam("msg", e.getMessage()).build()).build();
        }
    }

    @GET
    @Path("/{username}")
    @Produces(MediaType.TEXT_HTML)
    public Response userAccount(@PathParam("username") String username) throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        put("user", config.getUser(username));
        put("groupsUri", uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getGroups",String.class)).build(username));
        return view("manage/user.vm");
    }

    @POST
    @Path("/{username}")
    @Produces(MediaType.TEXT_HTML)
    public Response updateUserPassword(@PathParam("username") String username, @FormParam("password") String password) 
            throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException 
    {
        if (password == null) throw new IllegalArgumentException();
        User user = config.getUser(username);
        user.setPassword("", password);
        config.updateUser(user);
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(HomeResource.class,"index").build()).build();
    }

    @GET
    @Path("/{username}/groups")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getGroups(@PathParam("username") String username) {
        return config.getUser(username).getGroups();
    }
 
    @POST
    @Path("/{username}/groups")
    @Produces(MediaType.TEXT_HTML)
    public Response addUserGroup(@PathParam("username") String username, @FormParam("group") String group) 
            throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException 
    {
        User user = config.getUser(username);
        if (!user.getGroups().contains(group)) {
            user.getGroups().add(group);
            config.updateUser(user);
        }
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("userAccount",String.class)).build(username)).build();
    }

    @POST
    @Path("/{username}/groups/{group}")
    @Produces(MediaType.TEXT_HTML)
    public Response removeUserGroup(@PathParam("username") String username, @PathParam("group") String group) 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException 
    {
        User user = config.getUser(username);
        if (user.getGroups().contains(group)) {
            user.getGroups().remove(group);
            config.updateUser(user);
        }
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("userAccount",String.class)).build(username)).build();
    }

}
