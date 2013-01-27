package co.gridport.server.jms;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;

import javax.jms.JMSException;

import co.gridport.server.ClientThread;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;

public class ClientThreadJMS extends ClientThread {
	
	public ClientThreadJMS(RequestContext context) {
		super(context);
	}
	
	protected void complete() {
		//
	}
	
	public void execute() throws InterruptedException {
				
		if (!co.gridport.server.jms.Module.initialized) {
			try {
				serveText(500,"JMS Module not intialized");
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}			
			return;
		}
		
		Route E = context.routes.get(0);
		log.debug("Router context: " + E.context);
		if (context.URI.length()<E.context.length())
		{
		    context.URI = E.context;
		}
		String urc = context.URI.substring(E.context.length());
		if (urc.matches("^/?$"))
		{
			response.setHeader("Content-type", "text/html");
			String html = 
				"<h2>Subscribe to topic or queue</h2>"
				+"Request: <b>PUT topic/&lt;topic_filter&gt;</b> + <b>URL of the receiver in the entity</b><br/>"
				+"Request: <b>PUT queue/&lt;queue_filter&gt;</b> + <b>URL of the receiver in the entity</b><br/>"
				+"Response: <b>200 OK </b> - succesfully subscribed given receiver.<br/>"
				+"Response: <b>400 Bad Request</b> + <b>Error string</b> - failed to subscribe<br/>"
				+"Note: Upond subscribtion, the module will try to ping the receiver URL with a PUT request containg the topic filter in the endity and expects 201 Created to confirm the subscription."
				+"NOTE: Messages for the given filter will then be POSTed to the receiver's URL and will contain http headers representing the custom message headers and the follwing JMS internal headers:<br/>"
				+"<ul>"
				+"<li>JMSMessageID: string</li>"
				+"<li>JMSDestination: string</li>"
				+"<li>JMSPriority: int</li>"
				+"<li>JMSCorrelationID: string</li>"
				+"</ul>"
				+"NOTE: Receivers are expected to acknowledge the reception of messages with <b>202 Accepted</b> status code, " 
				+"otherwise will be rescheduled for delivery (and another POST) in 5 minute rounds."
				+"<h2>Unsubscribe from topic or queue</h2>"
				+"Request: <b>DELETE topic/&lt;topic_filter&gt;</b> + <b>URL of the receiver in the entity</b><br/>"
				+"Request: <b>DELETE queue/&lt;topic_filter&gt;</b> + <b>URL of the receiver in the entity</b><br/>"
				+"Response: <b>200 OK </b> - succesfully unsubscribed given receiver from the filter<br/>"
				+"Response: <b>400 Bad Request</b> + <b>Error string</b> - failed to unsubscribe<br/>"
				+"<h2>Publish a persistent Message</h2>"
				+"Request: <b>POST topic/&lt;topic&gt;</b> + <b>message content in the entity</b></br>"
				+"Request: <b>POST queue/&lt;queue&gt;</b> + <b>message content in the entity</b></br>"
				+"Response: <b>202 Accepted</b> + <b>Message ID</b> - message has been published and acknowledged by the JMS server.<br/>"
				+"Response: <b>400 Bad Request</b> + <b>Error string</b> - failed to publish the message<br/>"
				+"NOTE: All http request headers will be turned into jms message header properties or JMS attributes as follows:<br/>"					
				+"<ul>"
				+"<li>JMSPriority: int</li>"
				+"<li>JMSCorrelationID: string</li>"
				+"</ul>"
				+"NOTE: Messages are currently published without expiration ttl and the following headers are special JMS attributes:<br/>"
				+"<h2>List Subscribers</h2>"
				+"Request: <b>GET topic/&lt;topic_filter&gt;</b></br>"
				+"Request: <b>GET queue/&lt;queue_filter&gt;</b></br>"
				+"Response: <b>200 OK</b> + <b>text/plain of receiver URLs that are subscribed to the given topic/queue filter<b/><br/>"
			;
			try {
				serveText(200, html);
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}			
			return;
		} else if (!urc.matches("^(topic|queue)/.*")) {
			try {
				
				serveText(400, "Allowed URIs are "+E.context+"topic/* OR "+E.context+"queue/* ");
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}
			return;
		}
		String destType = urc.substring(0,urc .indexOf("/"));
		String destName = urc.substring(destType.length()+1).replaceAll("/", ".");
		String destination = destType+"://"+destName; 	
		
		//load incoming request
		try {
			load();
		} catch (IOException e1) {
			log.error(e1.getMessage(), e1);			
			return;
		}	
			
		//list subscriptions and available methods
		if (request.getMethod().equals("OPTIONS")) {
			log("JMS/1.1 LIST OPTIONS FOR "+destination);
			String[] list = co.gridport.server.jms.Module.listSubscribers(destination);
			String reply = new String();
			for(String item:list) {
				reply += item+"\r\n";
			}
			response.setHeader("Allow", "POST PUT DELETE OPTIONS"); //GET
			response.setHeader("Content-type", "text/plain");
			try {
				this.serveText(200,reply);
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}				
			return;
		}
		
		//list subscriptions and available methods
		if (request.getMethod().equals("GET")) {
			log("JMS/1.1 LIST "+destination);
			try {				
				String content = "";
				for(String receiver: co.gridport.server.jms.Module.listSubscribers(destination))
				{
					content += receiver + "\n";
				}
				response.setHeader("Content-type", "text/plain");
				this.serveText(200, content);
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}
			return;
		}
		
		//subscribe
		if (request.getMethod().equals("PUT")) {
			String target = new String(this.body);
			String error = co.gridport.server.jms.Module.addSubscriber(destination, target);			
			try {
				if (error == null) {
					log("JMS/1.1 SUBSCRIBED "+target +" TO "+destination);					
			        response.setStatus(200);
			        response.setContentLength(-1);
				} else {
					log("JMS/1.1 FAILED TO SUBSCRIBE "+target +" TO "+destination+" - " + error);				
					serveText(400,error);
				}
			} catch (IOException e) {
			    log.error(e.getMessage(), e);
			}
			return;
		}
		
		//unsubscribe
		if (request.getMethod().equals("DELETE")) {
			String target = new String(this.body);
			String error = co.gridport.server.jms.Module.removeSubscriber(destination, target);
			try {
				if (error == null) {
					log("JMS/1.1 UNSUBSCRIBED "+target +" FROM "+destination);
					response.setStatus(200);
		            response.setContentLength(-1);
				} else {
					log("JMS/1.1 FAILED TO UNSUBSCRIBE "+target +" FROM  "+destination);
					serveText(400,error);
				}
			} catch (IOException e) {
			    log.error(e.getMessage(), e);
			}
			return;
		}
		
		//publish 
		if (request.getMethod().equals("POST")) {
			String payload = new String(this.body);
			HashMap<String,String> properties = new HashMap<String,String>();
			for (Enumeration<String> headers = request.getHeaderNames() ; 
			    headers.hasMoreElements() ;
		    ) {			   
			    String header = headers.nextElement();
				for(Enumeration<String> values = request.getHeaders(header); 
				    values.hasMoreElements(); 
			    ) {
				    String value = values.nextElement();
					properties.put(header, value);
				}
			}
			try {
				try {
					String messageID = co.gridport.server.jms.Module.publish(destination, payload, properties,0);
					log("JMS/1.1 PUBLISH to "+destination);
					log(messageID);
					//send accepted + messageId
					serveText(202, messageID);
				} catch (JMSException e) {
					log.error("JMS/1.1 FAILED PUBLISH to "+destination, e);				
					serveText(400, e.getMessage());
				}
			} catch (IOException e) {
			    log.error(e.getMessage(), e);
			}				
			return;
		}
		
		//else unsupported method
		response.setStatus(405);
		response.setContentLength(-1);
		
	}	
	
	
}
