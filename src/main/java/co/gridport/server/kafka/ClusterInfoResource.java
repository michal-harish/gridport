package co.gridport.server.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

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
        Map<String,String> result = new TreeMap<String,String>();
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
    public TopicInfo getTopicInfo(@PathParam("topic") String topicName) throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        ClusterInfo cluster = ModuleKafka.getClusterInfo(zkServer);
        TopicInfo topic = cluster.getTopics().get(topicName);
        for(String category: topic.getConsumers().keySet()) {
            for(Entry<String,Object> consumer: topic.getConsumers().get(category).entrySet()) {
                consumer.setValue(getConsumerUrl(consumer.getKey()));
            }
        }
        return topic ;
    }

    @GET
    @Path("/consumers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String,Map<String,String>> getConsumerList() throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        ClusterInfo cluster = ModuleKafka.getClusterInfo(zkServer);
        @SuppressWarnings("serial")
        Map<String,Map<String,String>> result = new LinkedHashMap<String,Map<String,String>>() {{
            put("active", new TreeMap<String,String>());
            put("inactive", new TreeMap<String,String>());
        }};
        for(Entry<String,ConsumerInfo> entry: cluster.getConsumers().entrySet()) {
            String groupid = entry.getKey();
            ConsumerInfo consumer = entry.getValue();
            result
                .get(consumer.getProcesses().size() > 0 ? "active" : "inactive")
                .put(groupid, getConsumerUrl(groupid));
        }
        return result;
    }

    private String getConsumerUrl(String groupid) throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        return uriInfo.getBaseUriBuilder()
                .path(ClusterInfoResource.class)
                .path(ClusterInfoResource.class.getMethod("getConsumerInfo", String.class))
                .build(zkServer,groupid).toString();
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
        return cluster.getConsumers().get(groupid).getConsumerTopic(topicName);
    }

    @GET
    @Path("/brokers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String,BrokerInfo> getBrokerList() throws IllegalArgumentException, UriBuilderException, SecurityException, NoSuchMethodException {
        ClusterInfo cluster = ModuleKafka.getClusterInfo(zkServer);
        return cluster.getBrokers();
    }

}
