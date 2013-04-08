package co.gridport.server.manager;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import co.gridport.GridPortServer;

@Path("/contracts")
public class ContractsResource extends Resource{

    @Context HttpServletRequest request;

    @Path("/{name}")
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getContract(@PathParam("name") String contractName) {
        put("contract", GridPortServer.policyProvider.getContract(contractName));
        put("endpoints", GridPortServer.policyProvider.getEndpoints());
        return view("manage/contract.vm");
    }
}
