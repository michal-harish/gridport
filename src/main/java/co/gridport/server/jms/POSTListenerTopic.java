package co.gridport.server.jms;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.GridPortServer;


public class POSTListenerTopic extends POSTListener {
    protected static Logger log = LoggerFactory.getLogger("mod_jms");
    
	static public String type = "topic";
	private String name;
	private URL U;
	private String topicId;
    private Session session;
    private Connection connection;
    private MessageConsumer consumer;
	public POSTListenerTopic(Context ctx, String topicId, String target) throws IOException,MalformedURLException, JMSException, NamingException {
		super(ctx, topicId, target);
		this.name = target;
		this.U = new URL(target);
		this.topicId = topicId;
		//test the target consumer first		
		HttpURLConnection http = (HttpURLConnection) U.openConnection();
		http.setUseCaches(false);
		http.setRequestMethod("PUT");
		http.setDoOutput(true);
		http.setDoInput(true);
		OutputStream ost = http.getOutputStream();
		ost.write(topicId.getBytes());
		ost.flush();
		ost.close();
		http.getInputStream().close();
		http.disconnect();
		if (http.getResponseCode() == 201) { //201 Created
			//now create the JMS listener
			javax.jms.TopicConnectionFactory factory  = (javax.jms.TopicConnectionFactory)ctx.lookup("ConnectionFactory");
			connection = factory.createConnection();
	        connection.setClientID(GridPortServer.instanceId+":"+topicId);
	        session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);        			
			Topic topic = session.createTopic(topicId);
	        consumer = session.createDurableSubscriber(topic,name);        
	        consumer.setMessageListener(this);        
	        connection.start(); 
		} else {
			throw new IOException("Target consumer ["+target+"] did not respond with 201 Created."); 
		}
		//System.err.println(Thread.currentThread().getName() + " ID=" + Thread.currentThread().getId());			
	}
	
    private static String getText(Message m)  throws JMSException{
       	if (m instanceof TextMessage ) return ((TextMessage)m).getText();
        else return null;
    }
    
    public void onMessage(Message message) {      
    	//System.err.println(Thread.currentThread().getName() + " ID=" + Thread.currentThread().getId());
    	String h = "JMS/1.1 NOTIFY "+topicId+" ";
        try {        
        	h+=message.getJMSMessageID()+" ";
        	String text = getText(message);
        	if (text != null) {        							
				HttpURLConnection http = (HttpURLConnection) U.openConnection();
				http.setAllowUserInteraction(false);
				http.setUseCaches(false);
				http.setInstanceFollowRedirects(false);	
				http.setRequestMethod("POST");				
				http.addRequestProperty("JMSPriority",String.valueOf(message.getJMSPriority()));
				http.addRequestProperty("JMSDestination",message.getJMSDestination().toString());					
				http.addRequestProperty("JMSMessageID",message.getJMSMessageID());				 
				@SuppressWarnings("unchecked")
				Enumeration<String> propertyNames = message.getPropertyNames(); 
				while (propertyNames.hasMoreElements()) {
					String property = propertyNames.nextElement().toString();								        
					http.addRequestProperty(property,message.getStringProperty(property));
				}				
				http.setDoOutput(true);
				http.setDoInput(true);
				OutputStream ost = http.getOutputStream();
				ost.write(text.getBytes());
				ost.flush();
				ost.close();
				int status = http.getResponseCode();
				InputStream in = http.getInputStream();
				try {	
					if (status == 202) {
						log.info(h+"-> " + U.toString() + " : ACKNOWLEDGED (202 Accepted)");
						message.acknowledge();
					} else {
						int read;
						int zero_read = 0;		
						int response_len = 0;
						byte[] buffer = new byte[8192];
						byte[] response = new byte[0];
						do {											
							read = in.read(buffer, 0, buffer.length);
							
							if (read>0) {				
								byte[] x = new byte[response_len+read];
								if (response_len>0) System.arraycopy(response,0,x,0,response_len);
								response = x;
								System.arraycopy(buffer,0,response,response_len,read);
								response_len += read;
							} else {
								zero_read++;
								if (zero_read>3) throw new IOException("SubRequest.run() too many zero packets in the inputstream");
							}
						} while (read>=0);
						throw new IOException("Delivery response: " + new String(response));
					}
				} finally {
					in.close();
					http.disconnect();
				}
        	} else {
        		message.acknowledge();
        		log.warn("JMS Empty Message");
        	}
			
        } catch (JMSException e) {
        	log.error(h + ": JMS FAILURE NO RECOVERY",e);
        	synchronized(this) {
				try {
					wait(10);
				} catch (InterruptedException e1) {
					log.warn("Notification recovery wait interrupted");
				}
			}
		} catch (IOException e) {
			log.warn(h + ": FAILED I/O - RECOVERY in 1 minute");//,e);	
			try {
				synchronized(this) {
					wait(60*1000);				
				}
				session.recover();
			} catch (JMSException e2) {
				log.warn("Session failed to recover", e2);
			} catch (InterruptedException e1) {
				log.warn("Session recovery wait interrupted!");
			}
		}
       
    }

    public void unsubscribe() throws JMSException {
    	consumer.close();
    	session.unsubscribe(name);
    	close();
    }
    protected void close() throws JMSException {
    	consumer.close();
    	session.close();
    	connection.close(); 
    }
    @Override
    protected void finalize() throws Throwable {    	
    	close();
    	super.finalize();
    }

	public void onException(JMSException jsme) {
		Module.log.warn("onException Received in TopicListenerPOST");		
	}
}
