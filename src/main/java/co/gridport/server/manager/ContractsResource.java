package co.gridport.server.manager;

import java.util.Arrays;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilderException;

import co.gridport.server.config.ConfigProvider;
import co.gridport.server.domain.Contract;

@Path("/contracts")
public class ContractsResource extends Resource {

    @Context public HttpServletRequest request;
    @Context public ConfigProvider config;

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<Contract> getContracts() {
        return config.getContracts();
    }

    @POST
    @Path("")
    @Produces(MediaType.TEXT_HTML)
    public Response addContract(@FormParam("name") String name) 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        try {
            if (config.getContract(name) != null) {
                throw new IllegalArgumentException("Contract `"+name+"` already exists");
            }
            Contract contract = new Contract(name, null, 0L, 0, null, null);
            config.updateContract(contract);
            return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getContract",String.class)).build(name)).build();
        } catch (Exception e) { 
            e.printStackTrace();
            return Response.seeOther(uriInfo.getBaseUriBuilder().path(HomeResource.class).path(HomeResource.class.getMethod("index",String.class)).queryParam("msg", e.getMessage()).build()).build();
        }
    }


    @Path("/{name}")
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getContract(@PathParam("name") String name)
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        put("contract", config.getContract(name));
        put("endpoints", config.getEndpoints());
        put("groupsUri", uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getGroups",String.class)).build(name));
        put("ipFiltersUri", uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getIpFilters",String.class)).build(name));
        put("endpointsUri", uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getEndpoints",String.class)).build(name));
        return view("manage/contract.vm");
    }

    @Path("/{name}")
    @POST
    @Produces(MediaType.TEXT_HTML)
    public Response updateContract(
        @PathParam("name") String name, 
        @FormParam("interval") Long interval,
        @FormParam("frequency") Integer frequency
    ) throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        Contract contract = config.getContract(name);
        contract.setIntervalMs(interval);
        contract.setFrequency(frequency);
        config.updateContract(contract);
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getContract",String.class)).build(name)).build();
    }

    @GET
    @Path("/{name}/groups")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getGroups(@PathParam("name") String name) {
        return config.getContract(name).getGroups();
    }

    @POST
    @Path("/{name}/groups")
    @Produces(MediaType.TEXT_HTML)
    public Response addUserGroup(@PathParam("name") String name, @FormParam("group") String group) 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        Contract contract = config.getContract(name);
        contract.addGroup(group);
        config.updateContract(contract);
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getContract",String.class)).build(name)).build();
    }

    @POST
    @Path("/{name}/groups/{group}")
    @Produces(MediaType.TEXT_HTML)
    public Response removeUserGroup(@PathParam("name") String name, @PathParam("group") String group) 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        Contract contract = config.getContract(name);
        contract.removeGroup(group);
        config.updateContract(contract);
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getContract",String.class)).build(name)).build();
    }

    @GET
    @Path("/{name}/ipfilters")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getIpFilters(@PathParam("name") String name) {
        return config.getContract(name).getIpFilters();
    }

    @POST
    @Path("/{name}/ipfilters")
    @Produces(MediaType.TEXT_HTML)
    public Response addIpFilter(@PathParam("name") String name, @FormParam("filter") String filter) 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        Contract contract = config.getContract(name);
        contract.addIpFilter(filter);
        config.updateContract(contract);
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getContract",String.class)).build(name)).build();
    }

    @POST
    @Path("/{name}/ipfilters/{filter}")
    @Produces(MediaType.TEXT_HTML)
    public Response removeIpFilter(@PathParam("name") String name, @PathParam("filter") String filter) 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        Contract contract = config.getContract(name);
        contract.removeIpFilter(filter);
        config.updateContract(contract);
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getContract",String.class)).build(name)).build();
    }

    @GET
    @Path("/{name}/endpoints")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Integer> getEndpoints(@PathParam("name") String name) {
        return config.getContract(name).getEndpoints();
    }

    @POST
    @Path("/{name}/endpoints")
    @Produces(MediaType.TEXT_HTML)
    public Response updateAvailableEndpoints(@PathParam("name") String name, @FormParam("endpoint") Integer[] endpoints) 
        throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        Contract contract = config.getContract(name);
        contract.setEndpoints(Arrays.asList(endpoints));
        config.updateContract(contract);
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(this.getClass()).path(this.getClass().getMethod("getContract",String.class)).build(name)).build();
    }
}
