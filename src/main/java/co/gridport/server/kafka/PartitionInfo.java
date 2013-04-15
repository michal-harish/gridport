package co.gridport.server.kafka;

import org.codehaus.jackson.annotate.JsonIgnore;

public class PartitionInfo {

    private TopicInfo topic;
    private BrokerInfo broker;
    private Integer id;
    private String pathId;

    public PartitionInfo(TopicInfo topic, BrokerInfo broker, Integer id, String pathId) {
        this.topic = topic;
        this.broker = broker;
        this.id = id;
        this.pathId = pathId;
    }

    public Integer getId() {
        return id;
    }

    @JsonIgnore
    public TopicInfo getTopic() {
        return topic;
    }

    public long getSmallestOffset() {
        return broker.getConnector().getOffsetsBefore(topic.getName(), id, -2L, 1)[0];
    }

    public long getLargestOffset() {
        return broker.getConnector().getOffsetsBefore(topic.getName(), id, -1L, 1)[0];
    }

    public String getFullId() {
        return pathId;
    }


}
