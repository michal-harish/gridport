package co.gridport.server.kafka;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.I0Itec.zkclient.IZkChildListener;
import org.codehaus.jackson.annotate.JsonPropertyOrder;

@JsonPropertyOrder({"groupId","status"})
public class ConsumerInfo {

    private ClusterInfo cluster;
    private String groupid;
    private Map<String, ConsumptionStatus> topics;

    public ConsumerInfo(ClusterInfo cluster, String groupid) {
        this.cluster = cluster;
        this.groupid = groupid;
    }

    public String getGroupId() {
        return groupid;
    }

    public Map<String,Map<String,?>> getStatus() {
        Map<String,Map<String,?>> result = new HashMap<String,Map<String,?>>();
        Map<String, TopicConsumptionStatus> details = getConsumptionStatus();
        Map<String,Object> topics = new HashMap<String,Object>();
        for(Entry<String, TopicConsumptionStatus> entry : details.entrySet()) {
            topics.put(entry.getKey(), entry.getValue().consumed);
        }
        result.put("summary", topics);
        result.put("details", details);
        return result ;
    }

    public static class TopicConsumptionStatus {

        public Double consumed; 
        public Map<String,String> partitions = new HashMap<String,String>();
        public TopicConsumptionStatus(ConsumptionStatus consumption) {
            consumed = 0.0;
            Integer n = 0;
            Map<Integer, Long> committedOffsets = consumption.getLastConsumedOffset();
            for(PartitionInfo partition: consumption.getTopic().getPartitions().values()) {
                Long watermark = committedOffsets.get(partition.getId());
                Long available = Math.max(watermark, partition.getLargestOffset());
                partitions.put(partition.getFullId(), watermark +"/" + available);
                if (available>0) {
                    consumed += Double.valueOf(watermark) / Double.valueOf(available) * 100 ;
                    n++;
                }
            }
            consumed = consumed / n;
        }
    }

    private Map<String,TopicConsumptionStatus> getConsumptionStatus() {
        if (topics == null) {
            try {
                if (cluster.zk.exists("/consumers/"+groupid+"/offsets")) {
                    topics = new HashMap<String,ConsumptionStatus>();
                    setTopics(cluster.zk.getChildren("/consumers/"+groupid+"/offsets"));
                    cluster.zk.subscribeChildChanges("/consumers/"+groupid+"/offsets", new IZkChildListener() {
                        @Override
                        public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
                            setTopics(currentChilds);
                        }
                    });
                } else {
                    return new HashMap<String,TopicConsumptionStatus>();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        synchronized(topics) {
            Map<String,TopicConsumptionStatus> result = new HashMap<String,TopicConsumptionStatus>();
            for(ConsumptionStatus status: topics.values()) {
                result.put(status.getTopic().getName(), new TopicConsumptionStatus(status));
            }
            return result;
        }
    }

    private void setTopics(List<String> consumedTopics) {
        synchronized(topics) {
            for(String topicName: consumedTopics) {
                if (!topics.containsKey(topicName)) {
                    topics.put(topicName, new ConsumptionStatus(
                        cluster, 
                        cluster.getTopics().get(topicName),
                        this
                    ));
                }
            }
            for(String topicName: topics.keySet()) {
                if (!consumedTopics.contains(topicName)) {
                    topics.remove(topicName);
                }
            }
        }
    }
}
