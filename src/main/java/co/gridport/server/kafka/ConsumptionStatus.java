package co.gridport.server.kafka;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.I0Itec.zkclient.IZkDataListener;

public class ConsumptionStatus {

    private ClusterInfo cluster;
    private TopicInfo topic;
    private ConsumerInfo consumer;
    private Map<Integer,Long> committedOffsets;

    public ConsumptionStatus(ClusterInfo cluster, TopicInfo topic, ConsumerInfo consumer) {
        this.cluster = cluster;
        this.topic = topic;
        this.consumer = consumer;
        this.committedOffsets = new ConcurrentHashMap<Integer,Long>();
    }

    public TopicInfo getTopic() {
        return topic;
    }

    public Map<Integer,Long> getLastConsumedOffset() {
       for(PartitionInfo partition: topic.getPartitions().values()) {
           final String pathId = partition.getFullId();
           final Integer id = partition.getId();
            if (!committedOffsets.containsKey(partition)) {
                if (cluster.zk.exists("/consumers/"+consumer.getGroupId()+"/offsets/"+topic.getName()+"/"+pathId)) {
                    String watermark = cluster.zk.readData("/consumers/"+consumer.getGroupId()+"/offsets/"+topic.getName()+"/"+pathId);
                    committedOffsets.put(id, Long.valueOf(watermark));
                    cluster.zk.subscribeDataChanges("/consumers/"+consumer.getGroupId()+"/offsets/"+topic.getName()+"/"+pathId, new IZkDataListener() {
                        @Override
                        public void handleDataDeleted(String dataPath) throws Exception {
                            setCommittedOffset(id, 0L);
                            //TODO set up watcher for children again
                        }
                        @Override
                        public void handleDataChange(String dataPath, Object data) throws Exception {
                            setCommittedOffset(id, Long.valueOf(data.toString()));
                        }
                    });
                } else {
                    setCommittedOffset(partition.getId(), 0L);
                    //TODO watcher for children in "/consumers/"+consumer.getGroupId()+"/offsets/"+topic.getName()
                }
            }
       }
       synchronized(committedOffsets) {
           return Collections.unmodifiableMap(committedOffsets);
       }
    }

    private void setCommittedOffset(Integer id, long watermark) {
        committedOffsets.put(id, watermark);
    }

}
