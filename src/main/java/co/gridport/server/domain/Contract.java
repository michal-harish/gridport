package co.gridport.server.domain;

import co.gridport.server.utils.Utils;


public class Contract {
    //private static Logger log = LoggerFactory.getLogger("request");	

	protected String name;
	protected String[] endpoints = new String[0];
	protected Long intervalms; 
	protected Long last_request; 
	protected Long frequency;
	protected Long counter;
	protected String[] auth_group = new String[0];

    private String ip_range;
	
	public Contract(
	    final String name,
	    final String ip_range,
	    final Long intervalms,
	    final Long frequency,	    
	    final String[] auth_group,
	    final String[] endpoints
    ) 
	{
	    this.name = name;
	    this.ip_range = ip_range;
	    this.intervalms = intervalms;
	    this.frequency = frequency;
	    this.auth_group = auth_group;
	    this.endpoints = endpoints;
		last_request = new Long(0);
		counter = new Long(0);
	}
	
	public String getName() {
	    return name;
	}
	
	public String getIpRange() {
	    return ip_range;
	}
	
	public String[] getAuthGroup() {
	    return auth_group;
	}
	
	public long getIntervalMs() {
	    return intervalms;
	}

    public long getFrequency() {
        return frequency;
    }

    public long getCounter() {
        return counter;
    }

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
		
		if (auth_group.length == 0) {
			return true;
		} else for(String gC:auth_group) {
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
