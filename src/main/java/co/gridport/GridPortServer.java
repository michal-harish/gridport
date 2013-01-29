package co.gridport;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Scanner;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.ClientThread;
import co.gridport.server.ClientThreadRouter;
import co.gridport.server.handler.Authenticator;
import co.gridport.server.handler.Firewall;
import co.gridport.server.handler.RequestHandler;
import co.gridport.server.utils.Utils;

public class GridPortServer {
		
	public static String instanceId;
	public static int httpPort = 0;
	public static int sslPort = 0;
	public static String keyStoreFile = null;
	public static String keyStorePass = null;
	public static int generalTimeout = 3; //seconds		
	public static Connection policydb;
	
	public static SimpleDateFormat date = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss.SSS zzz");
	
	private static Server server;
	private static String policyDbFile = "./policy.db";
	
	private static boolean cliEnabled = false;
	
	private static Logger log = LoggerFactory.getLogger("server");
	
	public static void main(String[] args) {	
		
		if (args.length>0) {
			for(String A:args) {
				if (A.equals("cli")) cliEnabled=true;
			}			
		}	
		try {
			
			instanceId = "GRIDPORT-"+InetAddress.getLocalHost().getHostName();
			log.info("*** " + instanceId);

			connectPolicyDb();
			
			Runtime.getRuntime().addShutdownHook(new Thread() {      
			    @Override
			    public void run() {
			        shutdown();
			    }
			});
			
			ResultSet rs = policydb.createStatement().executeQuery("SELECT * FROM settings");
			while (rs.next()) if (!Utils.blank(rs.getString("name")) && !Utils.blank(rs.getString("value"))) {
				if (rs.getString("name").equals("httpPort")) httpPort = rs.getInt("value");
				if (rs.getString("name").equals("sslPort")) sslPort = rs.getInt("value");
				if (rs.getString("name").equals("keyStoreFile")) keyStoreFile = rs.getString("value");
				if (rs.getString("name").equals("keyStorePass")) keyStorePass = rs.getString("value");
				if (rs.getString("name").equals("generalTimeout")) generalTimeout = rs.getInt("value");
				if (rs.getString("name").equals("router.log")) ClientThreadRouter.logTopic = rs.getString("value"); 
			}		
			//open key store if configured
			if (!Utils.blank(keyStoreFile)) {
				System.setProperty("javax.net.ssl.trustStore", keyStoreFile);
				System.setProperty("javax.net.ssl.trustStorePassword", keyStorePass);
				log.info("*** TRUST KEYSTORE " +  keyStoreFile);
			}
			
			//initailize modules
			co.gridport.server.jms.Module.initialize();
			
			server = new Server();		
			server.setThreadPool(
                new QueuedThreadPool(50)
            );
			server.setGracefulShutdown(1000);

			if (httpPort > 0 )
			{
			    SelectChannelConnector connector1 = new SelectChannelConnector();
		        connector1.setPort(httpPort);
		        connector1.setMaxIdleTime(30000);	        		       		                 
		        server.addConnector(connector1);
		        log.info("*** HTTP CONNECTOR PORT " + httpPort);			    
			}
			if (sslPort > 0 ) {
			    SslContextFactory sslContextFactory = new SslContextFactory(keyStoreFile);
		        sslContextFactory.setKeyStorePassword(keyStorePass);
		        //sslContextFactory.setTrustStore(TRUSTSTORE_LOCATION);
		        //sslContextFactory.setTrustStorePassword(TRUSTSTORE_PASS);
		        //sslContextFactory.setNeedClientAuth(true);
			    SslSocketConnector connector2 = new SslSocketConnector(sslContextFactory);
		        connector2.setPort(sslPort);
		        server.addConnector(connector2);
		        log.info("*** SSL CONNECTOR PORT " + sslPort);
			}
			
			HandlerList serialHandlers = new HandlerList();
			HandlerCollection parallelHandlers = new HandlerCollection();   
	        serialHandlers.addHandler(new Firewall());
	        serialHandlers.addHandler(new Authenticator());
	        serialHandlers.addHandler(parallelHandlers);        
	        parallelHandlers.addHandler(new RequestHandler());
	        server.setHandler(serialHandlers);

			server.start();
	
		} catch (Exception e) {
			log.error("initialization critical failure ",e);
			System.exit(0);
		}
		
		log.info("***************************************");			
		
		if (cliEnabled) {
			log.info("(to install as service execute ./sh.daemon start)"); 
			String charsetName = "UTF-8";			
			Scanner scanner = new Scanner(new BufferedInputStream(System.in), charsetName);
			scanner.useLocale(new Locale("en", "GB"));						
			do {
				System.out.print("\n>");
				String cli = scanner.next();				
				if (cli.equals("exit")) {
					System.exit(0);
				}				
				if (cli.equals("flush")) { 
				    RequestHandler.notifyEventThreads();
				}				
			} while (true);
		} else {
			do {
				try {
					Thread.sleep(1000);
				} catch( InterruptedException e) {
					log.error("main interruption",e);						
				}				
			} while (true);
		}
	 
		
	}
	
	public static void shutdown() {
	    if (server != null)
	    {
    	    log.info("*** Shutting down GridPort server");
    	    try {
                server.stop();
            } catch (Exception e) {            
                e.printStackTrace();
            }
	    }
		
		//close modules
		co.gridport.server.jms.Module.close();
		co.gridport.server.space.Space2.close();
		
		//shut down plicy db
		try {
			log.info("*** Shutting down policy db");
			if (policydb !=null ) policydb.close();
		} catch (SQLException e2) {
			log.error("*** shut_down",e2);
		}
		
		
		log.info("*** SHUT DOWN COMPLETE ***");
		//Info(Thread.getAllStackTraces());		
	}

	private static void connectPolicyDb() throws SQLException,IOException,ClassNotFoundException   {
	    
		Class.forName("org.sqlite.JDBC");
		
		int version = 0;
		
		File f = new File(policyDbFile);		
	    if (f.exists()) {
	    	policydb = DriverManager.getConnection("jdbc:sqlite:" + policyDbFile);
	    } else  {	    	
	    	log.info("initializing " + policyDbFile);
	    	policydb = DriverManager.getConnection("jdbc:sqlite:" + policyDbFile);
	    	Statement s = policydb.createStatement();
	    	s.addBatch("CREATE TABLE endpoints (ssl NUMERIC, service_endpoint TEXT, http_method TEXT, gateway_host TEXT, ID INTEGER PRIMARY KEY, async TEXT, auth_group TEXT, gateway TEXT, uri_base TEXT);");
	    	s.addBatch("CREATE TABLE settings (name TEXT, value TEXT);");
	    	s.addBatch("CREATE TABLE users (groups TEXT, username TEXT);");
	    	s.addBatch("CREATE UNIQUE INDEX config ON settings(name ASC);");
	    	s.addBatch("CREATE UNIQUE INDEX user ON users(username ASC);");
	    	s.executeBatch();
	    	s.close();

	    	s.addBatch("INSERT INTO settings(name,value) VALUES('httpPort','8040')");
	    	s.addBatch("INSERT INTO settings(name,value) VALUES('sslPort','')");
	    	s.addBatch("INSERT INTO settings(name,value) VALUES('keyStoreFile','')");
	    	s.addBatch("INSERT INTO settings(name,value) VALUES('keyStorePass','')");
	    	s.addBatch("INSERT INTO settings(name,value) VALUES('generalTimeout','')");
            s.addBatch("INSERT INTO endpoints(ID,http_method,uri_base,service_endpoint) VALUES(1,'GET','/manage/*','module://manager')");
            s.addBatch("INSERT INTO endpoints(ID,http_method,uri_base,service_endpoint) VALUES(2,'GET POST MOVE PUT OPTIONS','/space/*','module://space')");	    	
            s.addBatch("INSERT INTO endpoints(ID,http_method,uri_base,service_endpoint) VALUES(3,'GET POST','/example/*','http://localhost:80/')");
	    	s.executeBatch();
	    	s.close();
	    	
	    	version = 6;
	    }
	    
		log.info("*** POLICY-DB CONNECTED: " + f.getAbsolutePath());		
		
		
		Statement s = policydb.createStatement();
		
		ResultSet rs;		
		rs = s.executeQuery("SELECT value FROM settings WHERE name='configVersion'"); 
		if (rs.next()) { 			
			version = rs.getInt("value");
		}		
		s.close();
		
		try {
    		if (version <1 ) { s.executeUpdate("ALTER TABLE endpoints ADD async TEXT "); s.close(); version = 1; }
    		if (version <2 ) { s.executeUpdate("ALTER TABLE endpoints ADD auth_group TEXT "); s.close(); version = 2; }		
    		if (version <3 ) { 
    				s.executeUpdate("CREATE TABLE users (groups TEXT, username TEXT) ");s.close();
    				s.executeUpdate("CREATE UNIQUE INDEX user ON users(username ASC)");s.close();
    				version = 3; 
    		}
    		if (version <4 ) {s.executeUpdate("ALTER TABLE endpoints ADD gateway TEXT"); s.close(); version = 4;}
    		if (version <5 ) {s.executeUpdate("ALTER TABLE endpoints ADD uri_base TEXT"); s.close(); version = 5;}
    		if (version <6 ) {
    			version = 6;
    		}
    		
    		if (version <7 ) {	
    			s.addBatch("CREATE TABLE contracts (name TEXT, content TEXT, ip_range TEXT, interval REAL, frequency INTEGER)");
    			s.executeBatch();
    			s.close(); 
    			version = 7;	
    		}
    		if (version <8 ) {
    		    s.addBatch("ALTER TABLE contracts ADD auth_group TEXT "); s.close();
    		    s.addBatch("INSERT INTO users(groups,username) VALUES('examplegroup','exampleuser')");
                s.addBatch("INSERT INTO contracts(name,content,ip_range) VALUES('localAdmin','1,2','127.0.0.1')");          
                s.addBatch("INSERT INTO contracts(name,content,interval,frequency,auth_group) VALUES('examplecontract','3',1.0,1,'examplegroup')");
                s.executeBatch();
                s.close(); 		    
    		    version = 8; 
    	    }
    		if (version <9 ) { 			
    	    	s.addBatch("CREATE TEMPORARY TABLE temp_table (ID INTEGER PRIMARY KEY,gateway_host TEXT, http_method TEXT, uri_base TEXT, ssl NUMERIC, async TEXT, service_endpoint TEXT, gateway TEXT)");
    	    	s.addBatch("INSERT INTO temp_table SELECT ID,gateway_host, http_method, uri_base, ssl, async, service_endpoint, gateway FROM endpoints");
    	    	s.addBatch("DROP TABLE endpoints");
    	    	s.addBatch("CREATE TABLE endpoints (ID INTEGER PRIMARY KEY,gateway_host TEXT, http_method TEXT, uri_base TEXT, ssl NUMERIC, async TEXT, service_endpoint TEXT, gateway TEXT)");
    	    	s.addBatch("INSERT INTO endpoints SELECT * FROM temp_table");
    	    	s.addBatch("DROP TABLE temp_table");
    			s.addBatch("DELETE FROM settings WHERE name='R-service-log'");
    			s.executeBatch();
    			s.close(); 
    			version = 9; 
    		}
		} finally {		
    		log.info("*** POLICY-DB Version: "+ version);		
    		s = policydb.createStatement();
    		s.addBatch("DELETE FROM settings WHERE name='configVersion'");
    		s.addBatch("INSERT INTO settings(name,value) VALUES('configVersion','"+version+"')");
    		s.executeBatch();
    		s.close();
		}		
	}
}
