package co.gridport.server.kafka;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilderException;
import javax.ws.rs.core.UriInfo;

import co.gridport.server.VelocityResource;

@Path("/{zk}")
public class ClusterInfoResource extends VelocityResource {

    @Context public UriInfo uriInfo;

    @PathParam("zk") String zkServer;

    @GET
    @Path("")
    @Produces(MediaType.TEXT_HTML)
    public Response getHelp() throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        put("topicListUrl", uriInfo.getBaseUriBuilder().path(ClusterInfoResource.class).path(ClusterInfoResource.class.getMethod("getTopicList")).build(zkServer));
        put("consumerListUrl", uriInfo.getBaseUriBuilder().path(ClusterInfoResource.class).path(ClusterInfoResource.class.getMethod("getConsumerList")).build(zkServer));
        put("brokerListUrl", uriInfo.getBaseUriBuilder().path(ClusterInfoResource.class).path(ClusterInfoResource.class.getMethod("getBrokerList")).build(zkServer));
        return view("kafka/help.vm");
    }

    @GET
    @Path("/topics")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String,String> getTopicList() throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        ClusterInfo cluster = ModuleKafka.getClusterInfo(zkServer);
        Map<String,String> result = new HashMap<String,String>();
        for(String topic: cluster.getTopics().keySet()) {
            result.put(
                topic,
                uriInfo.getBaseUriBuilder()
                    .path(ClusterInfoResource.class)
                    .path(ClusterInfoResource.class.getMethod("getTopicInfo", String.class))
                    .build(zkServer,topic).toString()
            );
        }
        return result;
    }

    @GET
    @Path("/topics/{topic}")
    @Produces(MediaType.APPLICATION_JSON)
    public TopicInfo getTopicInfo(@PathParam("topic") String topic) {
        ClusterInfo cluster = ModuleKafka.getClusterInfo(zkServer);
        return cluster.getTopics().get(topic);
    }

    @GET
    @Path("/consumers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String,String> getConsumerList() throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        ClusterInfo cluster = ModuleKafka.getClusterInfo(zkServer);
        Map<String,String> result = new HashMap<String,String>();
        for(String groupid: cluster.getConsumers().keySet()) {
            result.put(
                groupid,
                uriInfo.getBaseUriBuilder()
                    .path(ClusterInfoResource.class)
                    .path(ClusterInfoResource.class.getMethod("getConsumerInfo", String.class))
                    .build(zkServer,groupid).toString()
            );
        }
        return result;
    }

    @GET
    @Path("/consumers/{groupid}")
    @Produces(MediaType.APPLICATION_JSON)
    public ConsumerInfo getConsumerInfo(@PathParam("groupid") String groupid) throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        ClusterInfo cluster = ModuleKafka.getClusterInfo(zkServer);
        ConsumerInfo info = cluster.getConsumers().get(groupid);
        for(String topic: info.getStatus().keySet()) {
            info.getStatus().get(topic).put("url", uriInfo.getBaseUriBuilder()
            .path(ClusterInfoResource.class)
            .path(ClusterInfoResource.class.getMethod("getConsumerTopicInfo", String.class, String.class))
            .build(zkServer,groupid,topic).toString());
        }
        return info;
    }

    @GET
    @Path("/consumers/{groupid}/{topic}")
    @Produces(MediaType.APPLICATION_JSON)
    public ConsumerTopicInfo getConsumerTopicInfo(@PathParam("groupid") String groupid, @PathParam("topic") String topicName) {
        ClusterInfo cluster = ModuleKafka.getClusterInfo(zkServer);
        return cluster.getConsumers().get(groupid).getPartitions(topicName);
    }

    @GET
    @Path("/brokers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String,BrokerInfo> getBrokerList() throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        ClusterInfo cluster = ModuleKafka.getClusterInfo(zkServer);
        return cluster.getBrokers();
    }

}
