package co.gridport.server.kafka;

import kafka.javaapi.consumer.SimpleConsumer;

import org.codehaus.jackson.annotate.JsonIgnore;

public class BrokerInfo {

    private SimpleConsumer connector;
    private String host;
    private Integer port;

    public BrokerInfo(String address) {
        String[] addr = address.split(":");
        this.host = addr[0];
        this.port = addr.length>0 ? Integer.valueOf(addr[1]) : 9092;
    }

    public String getAddress() {
        return host+":"+port;
    }
    
    @JsonIgnore
    public SimpleConsumer getConnector() {
        if (connector == null) {
            connector = new SimpleConsumer(host,port,10000, 65535);
        }
        return connector;

    }

}
