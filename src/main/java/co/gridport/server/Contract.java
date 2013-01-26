package co.gridport.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.utils.Utils;


public class Contract {
    private static Logger log = LoggerFactory.getLogger("request");
    
	static private HashMap<String,Contract> contracts  = new HashMap<String,Contract>(); 
	static public ArrayList<Contract> getAvailableContracts(HttpServletRequest t) {
		ArrayList<Contract> result = new ArrayList<Contract>();
		String consumer_ip = t.getRemoteAddr();
    	if (t.getHeader("X-forwarded-for") != null) {
			consumer_ip  += "," + t.getHeader("X-forwarded-for");							
		}   
		String[] remoteIP = consumer_ip.split(",");			
		try {
			Statement sql = GridPortServer.policydb.createStatement();		
			try {
				ResultSet rs = sql.executeQuery("SELECT * FROM contracts");					
				while (rs.next()) {
					String name = rs.getString("name") == null ? "default" : rs.getString("name");
					Contract C;
					synchronized(contracts) {
						if (!contracts.containsKey(name)) new Contract(name);
						C = contracts.get(name);
						synchronized(C) {
							if (rs.getString("auth_group") != null && !rs.getString("auth_group").trim().equals("")) {
								ArrayList<String> a = new ArrayList<String>();
								for(String s:rs.getString("auth_group").trim().split("[\\s\\n\\r,]+")) {
									if (!Utils.blank(s.trim())) a.add(s.trim());
								}
								C.auth_group = a.toArray( new String[a.size()]);
							}
							C.frequency = rs.getInt("frequency");
							C.intervalms = Math.round(rs.getFloat("interval") * 1000);
							if (rs.getString("content") != null  && !rs.getString("content").trim().equals("")) {
								ArrayList<String> a = new ArrayList<String>();
								for(String s:rs.getString("content").trim().split("[\\s\\n\\r,]+")) {
									if (!Utils.blank(s.trim())) a.add(s.trim());									
								}
								C.endpoints =  a.toArray( new String[a.size()]);
							}
						}
					}

					String ip_range = rs.getString("ip_range");
					boolean within = (ip_range == null || ip_range.isEmpty());
					if (within) {
						//l.debug("available contract "+name+", ALL ALLOWED");
					} else {
						//l.debug("checking contract "+name+", IP RANGE");
						for(String range:rs.getString("ip_range").split("[,\n\r]")) {							
							if (range.contains("-")) {
								String[] r = range.split("-",2);
								try {
									byte[] ipLower = InetAddress.getByName(r[0].trim()).getAddress();								
									byte[] ipUpper = InetAddress.getByName(r[1].trim()).getAddress();		
									for(String ip:remoteIP) {
										byte[] ipRemote = InetAddress.getByName(ip).getAddress();
										boolean w = true;
										for(int i=0;i< (Math.min(ipLower.length,ipRemote.length));i++) {
											int lb = ipLower[i] < 0 ? 256+ ipLower[i] : ipLower[i];
											int ub = ipUpper[i] < 0 ? 256+ ipUpper[i] : ipUpper[i];
											int rb = ipRemote[i] < 0 ? 256+ ipRemote[i] : ipRemote[i];
											if (rb>=lb && rb<=ub) continue;
											w = false;
										}
										if (w) {
											within = true;
											//l.debug("available contract "+name+", IN RANGE="+range+" ");
											break;
										}
									}
									
								} catch(UnknownHostException e) {
									log.warn("Contract - unknown address ",e);
									continue;
								}
							} else {								
								//TODO wildcard matching								
								for(String IP:remoteIP) if (IP.trim().equals(range)) {
									//l.debug("available contract "+name+", EXACT MATCH="+IP);
									within = true;	
									break;
								}	
							}
							if (within) break; 
						}
					}
					if (within) {							
						result.add(C);						
					}
				} 
			} finally {
				sql.close();
			}			
		} catch (SQLException e) {			
			log.error("Contract SQL error",e);	
		}
		return result;
	}

	protected String name;
	protected String[] endpoints = new String[0];
	protected long intervalms; protected long last_request; 
	protected long frequency; protected long counter;
	protected String[] auth_group = new String[0];
	
	public Contract(String aName) {
		name = aName;
		contracts.put(aName,this);
		last_request = 0;
		counter = 0;
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
	
	public void consume(ClientThread t) throws InterruptedException {
		long sleep_ms = 0;
		while (true) {		
			if (sleep_ms>0) {
			    ClientThread.log.warn("WAITING FOR SLA SLOT");
				synchronized(this) {
					t.log("Waiting "+sleep_ms+" ms ("+this.name+": FQ = "+this.frequency+"/"+this.intervalms+" ms )");
				}
				Thread.sleep(sleep_ms);				
			}
			synchronized(this) {
				sleep_ms = this.intervalms - (System.currentTimeMillis() - this.last_request);							
				if (this.frequency>0 && this.counter<this.frequency) {
					if (last_request == 0) this.last_request = System.currentTimeMillis();
					this.counter++;
				} else {
					if (sleep_ms>0) continue;
					this.counter = 0;		
					this.last_request = System.currentTimeMillis();
				}
				
				break;
			}
		}
	}
}
