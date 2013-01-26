package co.gridport.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.GridPortServer;
import co.gridport.server.utils.Utils;


abstract public class ClientThread extends Thread {

    static protected Logger log = LoggerFactory.getLogger("request");
    
	protected HttpServletRequest request;
	protected HttpServletResponse response;
	protected GridPortContext context;
	protected byte[] body;	
	protected int body_len;
	protected Date received;
	protected long receivedMillisec;
	protected boolean loaded = false;
	
	protected String merge_duration = "-";
	protected long merge_size = 0;
	protected Contract contract;
	protected String group = null;
		
	protected ArrayList<String> logList = new ArrayList<String>();

	abstract protected void execute() throws InterruptedException ;
	abstract protected void complete();
		
	public ClientThread(
        GridPortContext context
    ) {
		this.request = context.request;
		this.response = context.response;
		this.context = context;
	}	
	
	protected void log(String message) {
		logList.add(message);
	}	
	protected void flushLog() {				
		if (logList.size()>0) {
			String log4 = new String();
			for(String s:logList) {
				log4 += (log4.length()> 0 ? " | " : "")+s;
			}
			logList.clear();
			log.info(log4);
		}		 
	}	
	
	
	
	protected void load() throws IOException {		
		if (loaded) return;
        body_len = 0;
        loaded = true;        
        if (request.getHeader("Content-Type") != null) {        	
        	if (request.getHeader("Content-Length") == null) {        		
        		log("! No Content-Length for entity " + request.getHeader("Content-Type"));
        	} else {
				int body_size = Integer.valueOf(request.getHeader("Content-Length"));						
				body = new byte[body_size];									
				int read;
				int zero_read = 0;
				do {			
					read = request.getInputStream().read(body, body_len, Math.min(body_size-body_len,4096));				
					if (read>0) {
						body_len += read;
					} else
						if (zero_read++>512) throw new IOException("ClientTrhead.init() too many zero packets int the inputstream");
				} while (read>=0);	
				if (body_len != body.length)
					throw new IOException("Expecting content length: " + body.length+", actually read: " + body_len);
        	} 
        }             
        
	}	
	
	final public void run() {
		
		try {				
			
			//for(List<String> s:exchange.getRequestHeaders().values()) log(s.get(0));			
			log((context.ssl ? "SSL " : "") + request.getProtocol() + " " + request.getMethod() + " " + request.getRequestURI() +" FROM " + context.consumer_ip + " VIA " + "/" + context.gateway_host);

			//choose the best available contract and derive group
			contract = null; 
			String[] groups = (context.groups == null ? "" : context.groups).split("[\\s\\n\\r,]");
			if (groups.length==0 || Utils.blank(groups[0])) group=null; else group = groups[0];
			for(Route E:context.routes) {	
				for(Contract C:E.contracts) synchronized(C) {
					String found = null;
					if (C.auth_group.length>0) { // contract is only for some auth groups										
						for(String gC:C.auth_group) {
							for(String g:groups) if (g.trim().equals(gC.trim())) {
								found = g.trim(); 
								break;
							}
							if (found!=null) break;
						}
						if (found==null) continue;
					}
					if (contract == null) { 
						contract = C;
						if (found!=null) group = found;
					} else if (C.intervalms<contract.intervalms) {
						contract = C;
						if (found!=null) group = found;
					} else if (C.frequency-C.counter>contract.frequency-contract.counter) {
						contract = C;
						if (found!=null) group = found;
					}
				}
			}
			if (contract == null) {
				log("No contract available");
				response.setStatus(403);
				response.setContentLength(-1);
				return;
			}
		
			try {				
				//process and update shared contract
				log("USING " + contract.name);
				contract.consume(this);
				
				// execute implemented behaviour
				execute();	
				
				if (tasks.size() > 0) {
					//log("= Accept: " + exchange.getRequestHeaders().getFirst("Accept"));  					
					mergeTasks();
				} else {
					if (events.size() > 0) {
						//first event's
						int eventStatus = events.get(0).async_statusCode;
						switch(eventStatus ) {
							default:
							    response.setStatus(eventStatus);
							    response.setContentLength(-1);								
								log("= EVENT ACCEPTED WITH " + eventStatus);
								break;
							case 202: 
                                response.setStatus(202);
                                response.setContentLength(-1);                              
								log("= EVENT ACCEPTED WITH 202 Accepted");
								break;
							case 301:
							case 302:
							case 303:
							case 304:
							case 305:
							case 307:
								response.setHeader("Location", request.getRequestURI());
                                response.setStatus(eventStatus);
                                response.setContentLength(-1);                              
								log("= EVENT ACCEPTED WITH REDIRECT "+eventStatus);	
								break;
								
						}
						flushLog();
					} 
				}
				
		        mergeEvents();
			} catch (InterruptedException e) {
				synchronized(tasks) { for(SubRequest T:tasks) T.interrupt(); }
				synchronized(events) { for(SubRequest T:events) T.interrupt(); }
				String info = "Client Thread Killed " + logList.get(1) + " " + logList.get(0);
				log(info);
                response.setStatus(410);
                response.setContentLength(-1);                              
                //Gone
			}

		} catch (IOException e) {				
			log.error("ClientThread Execution",e);	
            response.setStatus(500);
            response.setContentLength(-1);                              

		} finally {
			
			//??? exchange.close();
			
			complete();
			
			synchronized(GridPortHandler.threads) {
				GridPortHandler.threads.remove(this);
			}			
			if (response.getStatus() == 404) {				
				log("TODO compensate for 4xx responses");
			}
			
			flushLog();			
		}
		
	}
		
	protected List<SubRequest> tasks = new ArrayList<SubRequest>();	
	protected List<SubRequest> events = new ArrayList<SubRequest>();
	
	public void notifyEvents()
	{
		synchronized(events) { 
			events.notify();
		}
	}
	public void mergeTasks() throws IOException,InterruptedException {						
		try {
			if (tasks.size() > 0) {				
				if (tasks.size() == 1) {
					try { // PASSTHROUGH	
						tasks.get(0).join();						
						if (((SubRequest)tasks.get(0)).error != null) {
							if (((SubRequest)tasks.get(0)).error != null) {
								log("= PASSTHROUGH " + tasks.get(0).conn.getURL().toString()+ " ERROR " + ((SubRequest)tasks.get(0)).error);
							}						
                            response.setStatus(500);
                            response.setContentLength(-1);                              
						} else {
							int content_length = replicateResponseHeaders(tasks.get(0));							
							if (tasks.get(0).statusCode == 301
							    || tasks.get(0).statusCode == 302
							) 
							{ //TODO catch 30x headers and replace location
								String location = response.getHeader("Location");
								String newloc = location.replaceFirst("https?://"+tasks.get(0).conn.getURL().getHost(), "http" + (context.ssl ? "s":"") +"://"+ context.gateway_host + tasks.get(0).base);
								log(tasks.get(0).statusCode + " ! " + location + " >> " + newloc); 
								response.setHeader("Location",newloc);
								log.debug("RESPONSE HEADER Location: " + response.getHeader("Location"));
							}
							response.setContentLength(content_length);
                            log.debug("RESPONSE HEADER Content-Length: " + response.getHeader("Content-Length"));
                            response.setStatus(tasks.get(0).statusCode);                            
                            
                            log.debug("RESPONSE STATUS " + response.getStatus());
							merge_size += serveTaskStream(tasks.get(0), content_length);
							/*
							if (tasks.get(0).response_len>0)
								exchange.getResponseHeaders().set("Content-Length",String.valueOf(tasks.get(0).response_len));
				        	exchange.sendResponseHeaders(tasks.get(0).statusCode, tasks.get(0).response_len);
				        	if (tasks.get(0).response_len > 0)
				        		exchange.getResponseBody().write(tasks.get(0).response, 0, tasks.get(0).response_len );
				            merge_size += tasks.get(0).response_len;
				        	*/
				        	merge_duration = String.valueOf( (double) ( System.currentTimeMillis() - received.getTime() ) / 1000 );
				        	log("= "+merge_duration+ " PASSTHROUGH " + tasks.get(0).statusCode + " " + tasks.get(0).conn.getURL().toString()+ " RESPONSE LENGTH " + merge_size);				        	
						}
					} catch (Exception e) {
						e.printStackTrace();
						log("= PASSTHROUGH FAILED " + tasks.get(0).conn.getRequestMethod() + " "+ tasks.get(0).conn.getURL().toString());
						log(e.getMessage());
					}
					
				} else {/* 	
			        String ContentType = null;        	       
			        for(SubRequest t: tasks) {
			        	try {	        		
			        		t.join();			        		
				        	if (ContentType == null) { // TODO MERGE consequent contentTypes are not validated 
				        		ContentType = t.conn.getContentType();
				        		replicateResponseHeaders(t);
				        	}
				        	merge_size += t.response_len;
				        	if (t.error != null) throw t.error;
				        	log("= MERGE " + t.statusCode + " " + t.conn.getURL().toString()+ " RESPONSE LENGTH " + t.response_len);
			        	} catch (Exception e) {	        		
			        		log("= MERGE FAILED " + t.conn.getRequestMethod() + " "+ t.conn.getURL().toString());		        		
			        		return;
			        	}
			        }
			        if (ContentType == null)
			        	throw new IOException("Unknown Content-Type");
			        
			        log("MERGE Content-Type = "+ContentType);
			        if (ContentType.equals("application/json")) {
				        exchange.getResponseHeaders().set("Content-Type", "application/json");
				        long total = merge_size + 2 + (tasks.size()-1); 
				        exchange.getResponseHeaders().set("Content-Length",String.valueOf(total));
				        exchange.sendResponseHeaders(200, total);
				        exchange.getResponseBody().write("[".getBytes());
				        int item = 0;
				        for(SubRequest t: tasks) {
				        	if (item>0) exchange.getResponseBody().write(",".getBytes());
				        	//exchange.getResponseBody().write("{".getBytes());
				        	exchange.getResponseBody().write(t.response, 0, t.response_len );
				        	//exchange.getResponseBody().write("}".getBytes());
				        	item++;
				        }
				        exchange.getResponseBody().write("]".getBytes());
			        } else {	
			        	//TODO generate timestamp based boundary
				        String boundary = "------------Bx0776xdf65d4fgsdf8-";	        
				        exchange.getResponseHeaders().set("Content-Type", "multiple/" + ContentType +",boundary=" + boundary);
				        exchange.getResponseHeaders().set("Content-Length",String.valueOf(merge_size + tasks.size() * boundary.length()));
				        exchange.sendResponseHeaders(200, merge_size + tasks.size() * boundary.length());	        
				        for(SubRequest t: tasks) {
				        	exchange.getResponseBody().write(boundary.getBytes());
				        	exchange.getResponseBody().write(t.response, 0, t.response_len );
				        }
			        }
			        
			        merge_duration = String.valueOf( (double) ( System.currentTimeMillis() - received.getTime() ) / 1000 );
					log("= " + merge_duration + " MERGE COMPLETE " + 200);
					*/
				}			
			}
		} finally {
			if (response.getStatus() < 0) {
                response.setStatus(500);
                response.setContentLength(-1);                              
			}
			response.getOutputStream().close();
		}
        		
	}	
	
	public void mergeEvents() throws IOException,InterruptedException {
		if (events.size()>0) {
			List<SubRequest> x = new ArrayList<SubRequest>();
			while(true) {
				//copy event list
				x.clear();
				synchronized(events) {					
					for(SubRequest t: events) {
						x.add(t);
					}
				}
				//join events
				int wait_minutes = 1;
		        for(SubRequest t: x) {
		        	t.join();                	
		        	synchronized(events) {
	        			events.remove(t);
	        		}
		        	if (t.statusCode <400 && t.error == null) {  
		        		log("= " + t.runtime + " ASYNC " + t.conn.getRequestMethod()+ " " + t.conn.getURL().toString() +"; HTTP STATUS="+ t.statusCode );		        		
		        	} else { // 4xx or 5xx or Connection Errors are requeued
		        		log("RETRY "+t.conn.getRequestMethod()+ " " + t.conn.getURL().toString()+ "; EVENT EXPECTS 2xx 3xx " + t.async_statusCode+ "; " + (t.error != null ? t.error : t.statusCode + " RETURNED"));
		        		flushLog();		        		
		        		new SubRequestMerge(this, t.conn.getURL().toString(),String.valueOf(t.async_statusCode)).start();		        				        		 	        	
		        	}
		        }		        
		        synchronized(events) {
		        	try {
				        if (events.size()==0 ) break; 			        	
				        else events.wait(wait_minutes * 60000);
				        wait_minutes++; // each time we wait one minute longer
		        	} catch(InterruptedException e) {
		        		log("= TODO Compensate for pending Event Subrequests");
		        		throw e;
		        	}
		        }
			}
		}
	}
	
	private int replicateResponseHeaders(SubRequest t) {
		String[] keys = new String[t.headers.keySet().size()];	
		t.headers.keySet().toArray(keys);
		int content_length = -1;
    	for ( int i=0; i<keys.length ; i++) {
    		String header =  keys[i];
    		if (header == null) continue;	        		
    		if (header.toLowerCase().equals("transfer-encoding")) continue; 
    		if (header.toLowerCase().equals("content-length")) 
    		{
    			content_length = Integer.valueOf(t.headers.get(header).get(0));
    			continue;    		
    		}
    		for( int j =0; j<t.headers.get(header).size(); j++) {
	    		String value = t.headers.get(header).get(j);
	    		log.debug("RESPONSE HEADER "+ header + ": " + value);	    		
		    	response.addHeader(header,value);
    		}    		    		    		
    	}
    	return content_length;
	}
	
	protected int serveTaskStream(SubRequest task, int content_length) throws IOException
	{
		byte[] buffer = new byte[8192];
		int read;
		int zero_read = 0;		
		int response_len = 0;
		InputStream in = null;
		DataOutputStream out = null;
		try {
			out = new DataOutputStream(response.getOutputStream());
			in = task.conn.getInputStream();
			//@TODO catch all exceptions caused by 4xx satus codes on getinputstream
		} catch (IOException xxx) {//FileNotFoundException e404) {
			log(xxx.getMessage());
			in = task.conn.getErrorStream();
			if (!(in instanceof InputStream)) {					
				return 0;
			}
		}
		do {			    
			read = in.read(buffer, 0, buffer.length);
			log.debug("SERVE TASK STREAM READ " + read);
			if (read>0) {
				out.write(buffer, 0, read);
				response_len += read;
				
			} else {
				zero_read++;
				if (zero_read>3) throw new IOException("Too many zero packets inputstream whil serveTaskStream() execution");
			}
		} while (read>=0);
		return response_len;
	}
	
	protected void serveFile(String filename) throws IOException {
		File inFile = new File(filename);
		if (!inFile.isFile()) {
            response.setStatus(404);
            response.setContentLength(-1);
			return;
		} else {
			
			response.setHeader("Connection", "close");
			
			//TODO extension to mime type
			if (request.getHeader("If-Modified-Since") != null) {				
				String ifmod = request.getHeader("If-Modified-Since");
				try {
									
					if (inFile.lastModified() <= GridPortServer.date.parse(ifmod).getTime()) {
                        response.setStatus(304);
                        response.setContentLength(-1);
						return;
					}	
				} catch (ParseException e) {
					log.warn("Invalid If-Modified-since time format: "+ifmod);					
				}				
							
			}
			//exchange.getResponseHeaders().add("Content-type", "application/xml");
			Date lm = new Date(inFile.lastModified());
								

			response.setHeader("Last-Modified", lm.toString());
			response.setStatus(200);
			response.setContentLength((int) inFile.length());
			DataInputStream in = new DataInputStream(new FileInputStream(inFile));
			DataOutputStream out = new DataOutputStream(response.getOutputStream());
			try {
				byte[] buffer = new byte[32768];
				while (true) { // exception deals catches EOF
				int size = in.read(buffer);
				if (size<0) throw new EOFException();   	        	
			    out.write(buffer,0,size);		        
			    merge_size += size;
			  }
			} catch (EOFException eof) {
				log("File sent: "+ String.valueOf(inFile.length()));		      
			} finally {
				in.close();
			}			
		}
	}

	protected void serveText(int statusCode,String text) throws IOException {		
        response.setHeader("Connection", "close");
        response.setHeader("Content-Length", String.valueOf(text.length()));
		if (response.getHeader("Content-Type") == null) {
		    response.setHeader("Content-Type","text/plain");
		}
		byte[] b = text.getBytes();		
		response.setStatus(statusCode);
		response.setContentLength(b.length);
		response.getOutputStream().write(b);
		merge_size += b.length;
	}
}
