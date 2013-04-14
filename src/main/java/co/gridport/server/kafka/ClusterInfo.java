package co.gridport.server.kafka;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;

public class ClusterInfo {
    protected ZkClient zk;
    private Map<String, BrokerInfo> brokers;
    private Map<String, ConsumerInfo> consumers;
    private Map<String, TopicInfo> topics;

    public ClusterInfo(String connectionString) {
        zk = new ZkClient(connectionString, 10000, 10000, new StringSerializer());
    }

    public void close() {
        if (zk !=null) zk.close();
        zk = null;
        for(BrokerInfo broker: brokers.values()) {
            broker.close();
        }
        brokers = null;
    }

    public Map<String, ConsumerInfo> getConsumers() {
        if (consumers == null) {
            consumers = new HashMap<String, ConsumerInfo>();
            try {
                setConsumers(zk.getChildren("/consumers"));
                zk.subscribeChildChanges("/consumers", new IZkChildListener() {
                    @Override
                    public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
                        setConsumers(currentChilds);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        synchronized(this.consumers) {
            return Collections.unmodifiableMap(consumers);
        }
    }

    private void setConsumers(List<String> newConsumerList) {
        synchronized(consumers) {
            for(String groupid: newConsumerList) {
                if (!consumers.containsKey(groupid)) {
                    consumers.put(groupid, new ConsumerInfo(this,groupid));
                }
            }
            for(String groupid: consumers.keySet()) {
                if (!newConsumerList.contains(groupid)) {
                    consumers.remove(groupid);
                }
            }
        }
    }

    public Map<String,TopicInfo> getTopics() {
        if (topics == null) {
            topics = new HashMap<String, TopicInfo>();
            try {
                setTopics(zk.getChildren("/brokers/topics"));
                zk.subscribeChildChanges("/brokers/topics", new IZkChildListener() {
                    @Override
                    public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
                        setTopics(currentChilds);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        synchronized(topics) {
            return Collections.unmodifiableMap(topics);
        }
    }

    private void setTopics(List<String> newTopicList) {
        synchronized(this.topics) {
            for(String topicName: newTopicList) {
                if (!topics.containsKey(topicName)) {
                    topics.put(topicName, new TopicInfo(this,topicName));
                }
            }
            for(String topic: topics.keySet()) {
                if (!newTopicList.contains(topic)) {
                    topics.remove(topic);
                }
            }
        }
    }

    public Map<String,BrokerInfo> getBrokers() {
        if (brokers == null) {
            brokers = new HashMap<String,BrokerInfo>();
            try {
                setBrokers(zk.getChildren("/brokers/ids"));
                zk.subscribeChildChanges("/brokers/ids", new IZkChildListener() {
                    @Override
                    public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
                        setBrokers(currentChilds);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        synchronized(brokers) {
            return Collections.unmodifiableMap(brokers);
        }
    }
    private void setBrokers(List<String> brokerIdList) {
        synchronized(brokers) {
            brokers.clear();
            for(String brokerid: brokerIdList) {
                String[] broker = zk.readData("/brokers/ids/"+brokerid, true).toString().split(":",2);
                brokers.put(brokerid, new BrokerInfo(broker[1]));
            }
        }
    }

    static class StringSerializer implements ZkSerializer {

        public StringSerializer() {}
        public Object deserialize(byte[] data) throws ZkMarshallingError {
            if (data == null) return null;
            return new String(data);
        }

        public byte[] serialize(Object data) throws ZkMarshallingError {
            return data.toString().getBytes();
        }
    }


}
