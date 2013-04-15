package co.gridport.server.kafka;

import java.util.HashMap;
import java.util.Map;

import org.I0Itec.zkclient.IZkDataListener;
import org.codehaus.jackson.annotate.JsonPropertyOrder;

@JsonPropertyOrder({"fullId","owner","rate","watermark","largestOffset","smallestOffset"})
public class PartitionOwnerInfo {

    private PartitionInfo partition;
    private String owner;
    private Long watermark;
    private ClusterInfo cluster;
    private ConsumerInfo consumer;
    private Map<String,Object> status;

    public PartitionOwnerInfo(ClusterInfo cluster, PartitionInfo partition, ConsumerInfo consumer) {
        this.cluster = cluster;
        this.partition = partition;
        this.consumer = consumer;
    }

    public String getFullId() {
        return partition.getFullId();
    }

    @SuppressWarnings("serial")
    public synchronized Map<String,Object> getStatus() {
        final Long now = System.currentTimeMillis();
        if (status == null || (Long)status.get("timestamp") <  now - 1000) {
            status = new HashMap<String,Object>() {{
                put("timestamp", now);
                Long origin = partition.getSmallestOffset();
                Long available = partition.getLargestOffset() - origin;
                put("available", available);
                Long watermark = getWatermark();
                if (watermark != null) {
                    Long consumed = getWatermark() - origin;
                    put("consumed", consumed);
                    Double rate = Double.valueOf(consumed) / Double.valueOf(available) * 100;
                    put("rate", rate);
                    put("data", formatBytes(consumed) + " / " + formatBytes(available));
                }
            }};
        }
        return status;
    }

    public Long getWatermark() {
        if (watermark == null) {
            String zkPath = "/consumers/"+consumer.getGroupId()+"/offsets/"+partition.getTopic().getName()+"/"+getFullId();
            if (cluster.zk.exists(zkPath)) {
                watermark = Long.valueOf(cluster.zk.readData(zkPath).toString());
                cluster.zk.subscribeDataChanges(zkPath, new IZkDataListener() {
                    @Override public void handleDataChange(String dataPath, Object data) throws Exception {
                        synchronized(this) { 
                            watermark = Long.valueOf(data.toString());
                            status = null;
                        }
                    }
                    @Override
                    public void handleDataDeleted(String dataPath) throws Exception {
                        synchronized(this) { 
                            watermark = null;
                            status = null;
                        }
                    }
                });
            }
        }
        synchronized(this) { 
            return watermark; 
        }
    }

    public String getOwner() {
        if (owner == null) {
            String zkPath = "/consumers/"+consumer.getGroupId()+"/owners/" + partition.getTopic().getName() +"/" + getFullId();
            if (cluster.zk.exists(zkPath)) {
                owner = cluster.zk.readData(zkPath);
                cluster.zk.subscribeDataChanges(zkPath, new IZkDataListener() {
                    @Override public void handleDataChange(String dataPath, Object data) throws Exception {
                        synchronized(this) {
                            status = null;
                            owner = data.toString();
                        }
                    }
                    @Override
                    public void handleDataDeleted(String dataPath) throws Exception {
                        synchronized(this) {
                            status = null;
                            owner = null;
                        }
                    }
                });
            }
        }
        synchronized(this) {
            return owner;
        }
    }

    static public String formatBytes(Long data) {
        if (data < 1024 ) {
            return data + " b";
        } else if (data < Math.pow(1024,2)) {
            return Math.round(Double.valueOf(data)/Math.pow(1024.0,1)*100.0)/100.0 + " Kb";
        } else if (data < Math.pow(1024,4)) {
            return Math.round(Double.valueOf(data)/Math.pow(1024.0,2)*100.0)/100.0 + " Mb";
        } else {
            return Math.round(Double.valueOf(data)/Math.pow(1024.0,3)*100.0)/100.0 + " Gb";
        }
    }

}
