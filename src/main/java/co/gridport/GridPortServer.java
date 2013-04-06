package co.gridport;

import java.io.BufferedInputStream;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.ClientThreadRouter;
import co.gridport.server.PolicyProvider;
import co.gridport.server.PolicyProviderSQLite;
import co.gridport.server.handler.Authenticator;
import co.gridport.server.handler.Firewall;
import co.gridport.server.handler.ProxyHandler;
import co.gridport.server.manager.RootResource;
import co.gridport.server.utils.Utils;

public class GridPortServer {

    public static String instanceId;
    public static int httpPort = 0;
    public static int sslPort = 0;
    public static String keyStoreFile = null;
    public static String keyStorePass = null;
    public static int generalTimeout = 3; //seconds


    public static SimpleDateFormat date = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss.SSS zzz");

    private static Server server;
    public static PolicyProvider policyProvider;

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

            policyProvider = new PolicyProviderSQLite();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override public void run() {
                    shutdown();
                }
            });

            Map<String,String> settings = policyProvider.getSettings();
            if (settings.containsKey("httpPort")) httpPort = Integer.valueOf(settings.get("httpPort"));
            if (settings.containsKey("sslPort")) sslPort = Integer.valueOf(settings.get("sslPort"));
            if (settings.containsKey("keyStoreFile")) keyStoreFile = settings.get("keyStoreFile");
            if (settings.containsKey("keyStorePass")) keyStorePass = settings.get("keyStorePass");
            if (settings.containsKey("generalTimeout")) generalTimeout = Integer.valueOf(settings.get("generalTimeout"));
            if (settings.containsKey("router.log")) ClientThreadRouter.logTopic = settings.get("router.log");

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

            //competing context handlers
            ContextHandlerCollection contextHandlers = new ContextHandlerCollection();

            ContextHandler requestHandler = new ContextHandler("/");
            requestHandler.setHandler(new ProxyHandler());
            contextHandlers.addHandler(requestHandler);

            ServletContextHandler managerContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            managerContextHandler.setContextPath("/manage");
            ServletHolder s = new ServletHolder(new HttpServletDispatcher());
            s.setInitOrder(1);
            s.setInitParameter("resteasy.scan", "false");
            s.setInitParameter("resteasy.resources",RootResource.class.getName());
            managerContextHandler.addServlet(s,"/*");
            contextHandlers.addHandler(managerContextHandler);

            //all requests pass through the following chain of handlers:
            HandlerList serialHandlers = new HandlerList();
            serialHandlers.addHandler(new Firewall());
            serialHandlers.addHandler(new Authenticator());
            serialHandlers.addHandler(contextHandlers);
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
                    ProxyHandler.notifyEventThreads();
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

        policyProvider.close();

        log.info("*** SHUT DOWN COMPLETE ***");
        //Info(Thread.getAllStackTraces());        
    }

}
