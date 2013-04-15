package co.gridport.server.kafka;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonPropertyOrder;

@JsonPropertyOrder({"status","partitions"})
public class ConsumerTopicInfo {

    static protected SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");

    private ClusterInfo cluster;
    private TopicInfo topic;
    private ConsumerInfo consumer;
    //private Map<String,Long> committedOffsets new ConcurrentHashMap<String,Long>();
    private Map<String, PartitionOwnerInfo> partitions;
    private Map<String,Object> status;

    public ConsumerTopicInfo(ClusterInfo cluster, TopicInfo topic, ConsumerInfo consumer) {
        this.cluster = cluster;
        this.topic = topic;
        this.consumer = consumer;
        this.status = new HashMap<String,Object>();
    }

    public Map<String,Object> getStatus() {
        getPartitions();
        updateStatus();
        return status;
    }
    
    public void updateStatus() {
        Long available = 0L;
        Long consumed = 0L;
        Integer activeStreams = 0;
        Long timestamp = 0L;
        for(PartitionOwnerInfo partition: partitions.values()) {
            available += (Long) partition.getStatus().get("available");
            activeStreams += partition.getOwner() == null ? 0 : 1;
            timestamp = Math.max(timestamp, (Long) partition.getStatus().get("timestamp"));
            if (partition.getStatus().get("consumed") != null) {
                consumed += (Long) partition.getStatus().get("consumed");
            }
        }
        final Double rate = Double.valueOf(consumed) / Double.valueOf(available) * 100;
        final String info = PartitionOwnerInfo.formatBytes(consumed) + " / " + PartitionOwnerInfo.formatBytes(available);
        final Boolean live = activeStreams.equals(partitions.size());
        final String streams = activeStreams + " / " + partitions.size();
        synchronized(this) {
            status.clear();
            status.put("live", live);
            status.put("rate", rate);
            status.put("data", info);
            status.put("streams", streams);
            status.put("timestamp", dateFormatter.format(new Date(timestamp)));
        }
    }

    @SuppressWarnings("serial")
    public Map<String, PartitionOwnerInfo> getPartitions() {
        if (partitions == null) {
            partitions = new HashMap<String, PartitionOwnerInfo>() {{
                synchronized(this) {
                    for(Map.Entry<String,PartitionInfo> entry: topic.getPartitions().entrySet()) {
                        put(entry.getKey(), new PartitionOwnerInfo(cluster, entry.getValue(), consumer));
                    }
                }
            }};
        }
        return Collections.unmodifiableMap(partitions);
    }

}
