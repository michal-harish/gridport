package co.gridport.server.kafka;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TopicInfo {

    private ClusterInfo cluster;

    private String name;

    private Map<String,PartitionInfo> partitions;

    public TopicInfo(ClusterInfo cluster, String topicName) {
        this.cluster = cluster;
        this.name = topicName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String,PartitionInfo> getPartitions() {
        if (partitions == null) {
            partitions = new HashMap<String,PartitionInfo>();
            try {
                setPartitions(cluster.zk.getChildren("/brokers/topics/"+name));
                //TODO set up watch /brokers/topics/name
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        synchronized(partitions) {
            return Collections.unmodifiableMap(partitions);
        }
    }

    private void setPartitions(List<String> brokerIds) {
        Map<String,BrokerInfo> brokers = cluster.getBrokers();
        Map<String,PartitionInfo> newPartitionList = new HashMap<String,PartitionInfo>();
        for(String brokerId: brokerIds) {
            Integer brokerPartitionCount = Integer.valueOf(cluster.zk.readData("/brokers/topics/"+name+"/"+brokerId).toString());
            for(int partition=0; partition<brokerPartitionCount; partition++) {
                BrokerInfo broker = brokers.get(brokerId);
                String pathId = brokerId+"-"+partition;
                newPartitionList.put(pathId, new PartitionInfo(this,broker,partition,pathId));
            }
        }
        synchronized(partitions) {
            for(String id: newPartitionList.keySet()) {
                if (!partitions.containsKey(id)) {
                    partitions.put(id, newPartitionList.get(id));
                }
            }
            for(String id: partitions.keySet()) {
                if (!newPartitionList.containsKey(id)) {
                    partitions.remove(id);
                }
            }
        }
    }


}
