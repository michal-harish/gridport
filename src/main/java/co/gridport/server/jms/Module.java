package co.gridport.server.jms;
/**
 * This class implements methods around shared resources for all JMS Client Threads
 */

import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.GridPortServer;
import co.gridport.server.utils.Utils;

public class Module {
	static protected Logger log = LoggerFactory.getLogger("mod_jms");
	
	static public boolean initialized = false;
	static private HashMap<String,POSTListener> subs = new HashMap<String,POSTListener>();
	static private HashMap<String,Session> pubs= new HashMap<String,Session>();	
	static private javax.naming.Context ctx;
	static private javax.jms.Connection publishConnection;
	static private java.sql.Connection storage;
	static public void initialize() {		
		Logger log = LoggerFactory.getLogger("server");
		boolean loadSubscribers = false;		
		synchronized(subs) {
			if (!initialized) {				
				//open jms.db storage
				try {
					initializeStorage();

					//prepare factory
					String factory = null;
					String provider = null;				
					ResultSet rs;

					rs = GridPortServer.policydb.createStatement().executeQuery("SELECT * FROM settings WHERE name LIKE 'java.naming.factory.initial'");
				
					if (rs.next()) {
						factory = rs.getString("value");
					} else {
						factory = "org.apache.activemq.jndi.ActiveMQInitialContextFactory";
						GridPortServer.policydb.createStatement().execute("INSERT INTO settings(name,value) VALUES('java.naming.factory.initial','"+factory+"')");
					}
					rs.close();									
					rs = GridPortServer.policydb.createStatement().executeQuery("SELECT * FROM settings WHERE name LIKE 'java.naming.provider.url'");
					if (rs.next()) {
						provider = rs.getString("value");
					} else {
						provider = "failover:(tcp://localhost:61616)?randomize=falsee&startupMaxReconnectAttempts=3";
						GridPortServer.policydb.createStatement().execute("INSERT INTO settings(name,value) VALUES('java.naming.provider.url','"+provider+"')");
					}
					rs.close();
					if (Utils.blank(factory) || Utils.blank(provider)) {
						return;
					}
					log.info("*** Initializing JMS Module");
					Hashtable<String,String> env = new Hashtable<String,String>();
					env.put(Context.INITIAL_CONTEXT_FACTORY,factory);
					env.put(Context.PROVIDER_URL, provider);
					ctx = new InitialContext(env);
				} catch (Exception e) {
					log.error("*** JMS Module Initialization Failure",e);
					return;
				}
				loadSubscribers = true;
				initialized = true;
			}
		}
		if (loadSubscribers) {
			//initialize persistent connections			
			Statement stat;
			try {
				stat = storage.createStatement();
				ResultSet rs = stat.executeQuery("SELECT * FROM subscribers");
				String purge = "";
		        while(rs.next()) {		        	
		        	int id = rs.getInt("id");
		        	String destination = rs.getString("topic");
		        	String target = rs.getString("target");
		        	String sub = destination+"->"+target;
		        	synchronized(subs) {				
						if (subs.containsKey(sub)) {
							purge += (purge!="" ? "," : "") + id;
						} else {
							try {
								createListener(destination,target);							
								log.info("JMS/1.1 DURABLE SUBSCRIPTION "+target+" TO "+destination);
							} catch (MalformedURLException e) {
								//drop subscriber
								log.warn("JMS/1.1 FAILED TO REINSTANTIATE DURABLE SUBSCRIPTION - INVALID CONSUMER URL - DROPPING SUBSCRIPTION");
								purge += (purge!="" ? "," : "") + id;
							} catch (IOException e) {
								//skip but keep subscriber
								log.warn("JMS/1.1 FAILED TO REINSTANTIATE DURABLE SUBSCRIPTION "+target+" TO "+destination+": ",e);
							} catch (Exception e) {
								//quit initialization
								log.error("*** JMS Module Initialization Failure",e);
								close();
								return;
							}							
							
						}
					}
		        }
		        rs.close(); 
		        
		        if (purge!="") {
		        	stat.execute("DELETE FROM subscribers WHERE id IN ("+purge+")");
		        }
				stat.close();
			} catch (SQLException e) {
				initialized = false;
				log.error("*** JMS Module Initialization Failure",e);
			}				
		}
	}
	
	static public void close() {
		if (initialized) {
			
			//close storage
			try {
				storage.close();
			} catch (SQLException e) {
				log.error("JMS Module Close Storage",e);
			}
			
			//close all subscription listeners
			for(String sub:subs.keySet()) {
				POSTListener l = subs.remove(sub);
				log.info("Shutting down JMS listener");
				try {
					l.close();
				} catch (JMSException e) {
					log.error("JMS Module Shut Down Subscribers",e);
				} 
			}
			//close all publisher sessions
			for(String pub:pubs.keySet()) {
				log.info("Shutting down JMS publisher session");
				try {
					pubs.remove(pub).close();
				} catch (JMSException e) {
					log.error("JMS Module Shut Down Publisher Sessions",e);
				}				
			}			
			//close publisher connection
			if (publishConnection!=null) {
				log.info("Shutting down JMS publisher connection");
				try {
					publishConnection.close();
				} catch (JMSException e) {
					log.error("JMS Module Shut Down Publisher Connection",e);
				}			
			}			

			initialized = false;
		}
	}
	
	static public void createListener(String destination, String target) throws MalformedURLException, IOException, JMSException, NamingException {
		POSTListener newListener;
		//create runtime subscription
		String destType = destination.substring(0,8); 
		String destName = destination.substring(8);
		if (destType.equals("topic://")) {
			newListener = new POSTListenerTopic(ctx,destName, target);
		} else if (destType.equals("queue")) {
			throw new NamingException("Unimplemented JMS destination type"+destType);
			//newListener = new POSTListenerQueue(ctx,destName, target);
		} else {
			throw new NamingException("Unknown JMS destination type"+destType);
		}
		synchronized(subs) {
			subs.put(destination + "->" + target,newListener);							
		}		
	}
	static public String addSubscriber(String destination, String target) {		
		try {
			String sub = destination + "->" + target;
			//check if already subscribed
			synchronized(subs) {				
				if (subs.containsKey(sub)) {
					return null; //no error
				}
			}
			//create runtime subscription 
			createListener(destination,target);
			
			//create persistent subscription
			try {
				Statement stat = storage.createStatement();
				stat.execute("INSERT INTO subscribers(topic,target) VALUES('"+escape(destination)+"','"+escape(target)+"')");
				stat.close();
			} catch (SQLException e) {
				log.error("jms.module.addSubscriber",e);
				return "Failed to persist subscription"; 
			}			
			return null; //OK
		} catch (JMSException e) {									
			log.error("jms.module.addSubscriber",e);
			return "JMS Subscription Error (see error log)";
		} catch (NamingException e) {			
			log.error("jms.module.addSubscriber",e);
			return "Internal Naming Error (see error log)";
		} catch (MalformedURLException e) {			
			log.error("Invalid Consumer URL");
			return "Invalid Consumer URL (see error log)";
		} catch (IOException e) {
			log.error("jms.module.addSubscriber",e);
			return "I/O error (see error log)";
		}
	}
	
	static public String removeSubscriber(String destination,String target) {
		String sub = destination + "->" + target;
		synchronized(subs) {
			if (subs.containsKey(sub)) {
				try {
					Statement stat = storage.createStatement();
					stat.execute("DELETE FROM subscribers WHERE topic='"+escape(destination)+"' AND target='"+escape(target)+"'");
					stat.close();
				} catch (SQLException e) {
					log.error("JMS/1.1 FAILED TO REMOVE SUBSCRIBER "+target+" FROM "+destination,e);
					return "Subscriber persistence failure (see error log)";  
				}
				POSTListener s = subs.remove(sub);
				try {
					s.unsubscribe();
				} catch (JMSException e) {					
					log.error("JMS/1.1 FAILED TO REMOVE SUBSCRIBER "+target+" FROM "+destination,e);
					return "JMS unsubscribe failure (see error log)"; 
				}
			} else {
				log.warn("subscription listener doesn't exist, jms unsubscribed anyway");
			}
		}
		return null; //OK
	}
	
	static public String[] listSubscribers(String destination) {
		ArrayList<String> list = new ArrayList<String>();
		synchronized(subs) {
			for(String sub:subs.keySet()) {
				//int s = sub.indexOf("->");			
				if (sub.substring(0,destination.length()).equals(destination)) {
					//list.add(sub.substring(0, s-1) + " " + sub.substring(s+2));
					list.add(sub);
				}
			}
		}
		return list.toArray(new String[list.size()]);
	}
	
	static public String publish(String destination, String payload, HashMap<String,String> properties, long ttl) throws JMSException {

		Session session;
		synchronized(pubs) {
			if (publishConnection == null) {
				try {
					javax.jms.TopicConnectionFactory f  = (javax.jms.TopicConnectionFactory)ctx.lookup("ConnectionFactory");
					publishConnection = f.createConnection();
				} catch (NamingException e) {
					return "Internal naming error (see error log)";
				}
			}	
			if (!pubs.containsKey(destination)) {
				log.debug("creating jms publish session for " + destination);
				pubs.put(destination,publishConnection.createSession(false, Session.AUTO_ACKNOWLEDGE));
			}
			session = pubs.get(destination);
		}			
		Destination d;
		String destType = destination.substring(0,8); 
		String destName = destination.substring(8);
		if (destType.equals("topic://")) {
			d = session.createTopic(destName);
		} else if (destType.equals("queue://")) {
			d = session.createQueue(destName);
		} else {
			log.warn("JMS/1.1 FAILED TO PUBLISH TO "+destination+" - Unknown JMS destination type "+destType);
			return "Unknown JMS destination type "+destType;
		}
		MessageProducer publisher = session.createProducer(d); 
		try {
			publisher.setDeliveryMode(DeliveryMode.PERSISTENT);
			Message message = session.createTextMessage(payload);
			int priority = 4;
			//if (ttl>0) message.setJMSExpiration(ttl);
			for(String propertyName:properties.keySet()) {
				String propertyValue = properties.get(propertyName);
				if (propertyName.equals("JMSPriority"))
				{
					priority = Integer.valueOf(propertyValue);
				}
				else
				{
					message.setStringProperty(propertyName, propertyValue);
				}
			}
			log.debug("sending jms message to "+destType+"://"+destName);
			publisher.send(message, DeliveryMode.PERSISTENT, priority, ttl );
			message.getJMSMessageID();
			publisher.close();
			log.debug("jms log sent");			
			return message.getJMSMessageID();
		} finally {
			publisher.close();
		}
	}
	
	static private void initializeStorage() throws ClassNotFoundException, SQLException {
		Class.forName("org.sqlite.JDBC");				
		storage = DriverManager.getConnection("jdbc:sqlite:./jms.db");
		Statement stat = storage.createStatement();
		stat.execute("CREATE TABLE IF NOT EXISTS subscribers (" +
				"id INTEGER PRIMARY KEY AUTOINCREMENT" +
				",topic"+
				",target"+
				")");
		stat.close();
	}
	static private String escape(String data) {
		if (data == null) return "";
		else return data.replace("'","''");
	}
}
