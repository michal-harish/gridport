package co.gridport.server.domain;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnore;

import co.gridport.server.utils.Utils;


public class Contract {
    //private static Logger log = LoggerFactory.getLogger("request");

    protected String name;
    protected String[] endpoints = new String[0];
    protected Long intervalms; 
    protected Long last_request; 
    protected Long frequency;
    protected Long counter;
    protected List<String> groups = new ArrayList<String>();

    private String ip_range;

    public Contract(
        final String name,
        final String ip_range,
        final Long intervalms,
        final Long frequency,
        final List<String> groups,
        final String[] endpoints
    ) 
    {
        this.name = name;
        this.ip_range = ip_range == null ? "" : ip_range;
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

    public String getIpRange() {
        return ip_range;
    }

    public List<String> getGroups() {
        return groups;
    }

    @JsonIgnore
    public String getAuthGroupList() {
        return StringUtils.join(groups,",");
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

    @JsonIgnore
    public String getEndpointList() {
        return StringUtils.join(endpoints,",");
    }

    public String[] getEndpoints() {
        return endpoints;
    }

    public synchronized boolean hasEndpoint(String ID) {
        if (!Utils.blank(ID)) {
            if (endpoints.length == 0) {
                return true;
            }
            for(String endpointID:endpoints) {
                if (ID.equals(endpointID.trim())) {
                    return true;
                }
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
