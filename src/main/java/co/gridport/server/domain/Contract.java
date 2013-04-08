package co.gridport.server.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;


public class Contract {
    //private static Logger log = LoggerFactory.getLogger("request");

    protected String name;
    protected List<Integer> endpoints;
    protected Long intervalms; 
    protected Long last_request; 
    protected Long frequency;
    protected Long counter;
    protected List<String> groups = new ArrayList<String>();

    private List<String> ipFilters;

    public Contract(
        final String name,
        final String ipFilters,
        final Long intervalms,
        final Long frequency,
        final List<String> groups,
        final List<Integer> endpoints
    ) 
    {
        this.name = name;
        this.ipFilters = ipFilters == null ? new ArrayList<String>() : Arrays.asList(ipFilters.split("[,\n\r]"));
        this.intervalms = intervalms;
        this.frequency = frequency;
        this.groups = groups;
        this.endpoints = endpoints;
        last_request = 0L;
        counter = 0L;
    }

    public String getName() {
        return name;
    }

    public List<String> getIpFilters() {
        return ipFilters;
    }

    public List<String> getGroups() {
        return groups;
    }

    public long getIntervalMs() {
        return intervalms;
    }

    public long getFrequency() {
        return frequency;
    }

    @JsonIgnore
    public long getCounter() {
        return counter;
    }

    @JsonIgnore
    public long getLastRequest() {
        return last_request;
    }

    public void markCounter() {
        if (last_request == 0) {
            last_request = System.currentTimeMillis();
        }
        counter++;
    }

    public void resetCounter() {
        counter = new Long(0);
        last_request = System.currentTimeMillis();
    }

    public void setEndpoints(List<Integer> endpoints) {
        this.endpoints = endpoints;
    }

    public List<Integer> getEndpoints() {
        return endpoints;
    }

    public synchronized boolean hasEndpoint(Integer ID) {
        if (endpoints.size()== 0) {
            return true;
        }
        for(Integer endpointID:endpoints) {
            if (ID.equals(endpointID)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean hasEitherGroup(String[] groups) {

        if (groups.length == 0) {
            return true;
        } else for(String gC:groups) {
            if (groups.length ==0) {
                return true;
            } else for(String gE:groups) {
                if (gE.equals(gC)) {
                    return true;
                }
            }
        }
        return false;
    }

}
