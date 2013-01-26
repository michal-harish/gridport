package co.gridport.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.jms.JMSException;

import co.gridport.GridPortServer;
import co.gridport.server.utils.Utils;

public class ClientThreadRouter extends ClientThread {
	public static String logTopic = null; //settings/router.log
	
	public ClientThreadRouter(GridPortContext context) {
		super(context);
	}
	
	protected void execute() throws InterruptedException {
		try {	
			
			//1. initialize all subrequests within client thread
			for(Route E:context.routes) {	        	
		    	//TODO ROUTING SECONDARY consider compensation settings for each executed method after finding out failure;
				String URL = E.endpoint + E.uri + E.query_stirng;				
				try {			
					E.subrequest = new SubRequestMerge(this , URL, E.async);
					E.subrequest.base = E.uri;
		        	
				} catch (IOException e) {
					//TODO ROUTING SECONDARY consider compensation settings for each executed method after finding out failure;
					log.error("ROUTER SUBREQUEST",e);
				}
			}
			//combine all tasks and events into a single list
			List<SubRequest> subrequests = new ArrayList<SubRequest>();	
			subrequests.addAll(tasks);
			subrequests.addAll(events);			

			//2.stream client request to all subrequests in parallel 
			if (request.getHeader("Content-Type") != null) {        	
	        	if (request.getHeader("Content-Length") == null) {        		
	        		log("! No Content-Length for entity " + request.getHeader("Content-Type"));
	        	} else {
	        		int body_size = request.getIntHeader("Content-Length");
					byte[] buffer = new byte[8192];
					int read;
					int read_offset = 0;
					int zero_read = 0;
					InputStream is = request.getInputStream();
					do {
						int block_size = Math.min(body_size-read_offset, buffer.length);
						read = is.read(buffer, 0, block_size);
						for(SubRequest subrequest:subrequests)
						{
							if (read > 0
								&& !subrequest.conn.getRequestMethod().equals("OPTIONS")
								&& !subrequest.conn.getRequestMethod().equals("DELETE")
							)
							{
								try {
									System.err.println("Writing " + new String(buffer));
									subrequest.writeOut(buffer, read);
								} 
								catch (IOException e) 
								{
									log(e.getMessage());
								}
							}
						}
						if (read>0) {
							read_offset += read;
						} else {
							if (zero_read++>512) throw new IOException("ClientTrhead.init() too many zero packets int the inputstream");
						}
					} while (read>=0 && read_offset < body_size);
					if (read_offset != body_size)
					{
						throw new IOException("Expecting content length: " + body_size+", actually read: " + read_offset);
					}
	        	}
			}
			
			//3. start() all tasks and events to complete within their own thread space
			for(SubRequest subrequest:subrequests)
			{
				subrequest.start();
			}
		} catch (Exception e) {		
			log.error("ROUTER SUBREQUESTS",e);			
		}   
	}
	
	protected void complete() {
		//LOG into jms topic://gridport.log.router
		if (!Utils.blank(ClientThreadRouter.logTopic)) {
			if (co.gridport.server.jms.Module.initialized) {
				HashMap<String,String> log_properties = new HashMap<String,String>();
				log_properties.put("gridport-log-version", "1");													
				log_properties.put("gridport-log-date", GridPortServer.date.format(System.currentTimeMillis()));
				String log_payload = "gateway="+context.gateway_host+"\r\n";
				log_payload += "protocol="+(context.ssl ? "https":"http" ) + "\r\n";
				log_payload += "port="+context.port+"\r\n";
				log_payload += "request=" +  context.URI+context.QUERY_STRING+"\r\n";
				log_payload += "received=" + GridPortServer.date.format(received.getTime() ) +"\r\n";
				log_payload += "contract=" + contract.name +"\r\n";
				log_payload += "consumer.ip=" + this.context.consumer_ip +"\r\n";				
				log_payload += "consumer.id=" + this.context.username +"@" + group + "\r\n";				
				log_payload += "duration=" + String.valueOf( (double) ( System.currentTimeMillis() - received.getTime() ) / 1000 )+"\r\n";
				log_payload += "input="+body_len+"\r\n";
				log_payload += "output="+merge_size+"\r\n";
				log_payload += "volume="+(body_len+merge_size)+"\r\n";
				for(Route E:context.routes) {
					log_payload += "endpoint="+E.endpoint+"\r\n";
				}
				try {
					// week expiration for log messages
					co.gridport.server.jms.Module.publish(ClientThreadRouter.logTopic, log_payload, log_properties,60*60*24*7);
				} catch (JMSException e) {
					log.warn(ClientThreadRouter.logTopic+" ERROR " + e.getMessage());
				}
					
			} else {
				log.warn("JMS module not available for sending logs to "+ClientThreadRouter.logTopic);
			}
		}
	}
	
}
