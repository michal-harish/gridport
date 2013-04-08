package co.gridport;

import java.io.BufferedInputStream;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Scanner;

import org.eclipse.jetty.server.Connector;
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
import co.gridport.server.ConfigProvider;
import co.gridport.server.ConfigProviderSQLite;
import co.gridport.server.handler.Authenticator;
import co.gridport.server.handler.Firewall;
import co.gridport.server.handler.RequestHandler;
import co.gridport.server.manager.ContractsResource;
import co.gridport.server.manager.HomeResource;
import co.gridport.server.manager.LogsResource;
import co.gridport.server.manager.UsersResource;
import co.gridport.server.utils.Utils;

public class GridPortServer {

    public static String instanceId;

    public static SimpleDateFormat date = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss.SSS zzz");

    private static Server server;
    public static ConfigProvider policyProvider;

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

            createServer();
            reloadServerConfig();
            initializeModules();
            startServer();

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

    private static void createServer() {
        server = new Server();
        server.setThreadPool(
            new QueuedThreadPool(50)
        );
        server.setGracefulShutdown(1000);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() {
                shutdown();
            }
        });
    }

    public static void reloadServerConfig() throws Exception {
        stopServer();

        policyProvider = new ConfigProviderSQLite();

        if (policyProvider.has("router.log")) ClientThreadRouter.logTopic = policyProvider.get("router.log");

        if (policyProvider.has("keyStoreFile") && !Utils.blank(policyProvider.get("keyStoreFile"))) {
            System.setProperty("javax.net.ssl.trustStore", policyProvider.get("keyStoreFile"));
            System.setProperty("javax.net.ssl.trustStorePassword", policyProvider.get("keyStorePass"));
            log.info("*** TRUST KEYSTORE " +  policyProvider.get("keyStoreFile"));
        }

        if (policyProvider.has("httpPort"))
        {
            SelectChannelConnector connector1 = new SelectChannelConnector();
            connector1.setPort(Integer.valueOf(policyProvider.get("httpPort")));
            connector1.setMaxIdleTime(30000);
            server.addConnector(connector1);
            log.info("*** HTTP CONNECTOR PORT " + connector1.getPort());
        }
        if (policyProvider.has("sslPort")) {
            SslContextFactory sslContextFactory = new SslContextFactory(policyProvider.get("keyStoreFile"));
            sslContextFactory.setKeyStorePassword(policyProvider.get("keyStorePass"));
            //sslContextFactory.setTrustStore(TRUSTSTORE_LOCATION);
            //sslContextFactory.setTrustStorePassword(TRUSTSTORE_PASS);
            //sslContextFactory.setNeedClientAuth(true);

            SslSocketConnector connector2 = new SslSocketConnector(sslContextFactory);
            connector2.setPort(Integer.valueOf(policyProvider.get("sslPort")));
            server.addConnector(connector2);
            log.info("*** SSL CONNECTOR PORT " + connector2.getPort());
        }

        //competing context handlers
        ContextHandlerCollection contextHandlers = new ContextHandlerCollection();

        ContextHandler requestHandler = new ContextHandler("/");
        requestHandler.setHandler(new RequestHandler());
        contextHandlers.addHandler(requestHandler);

        ServletContextHandler managerContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        managerContextHandler.setContextPath("/manage");
        ServletHolder s = new ServletHolder(new HttpServletDispatcher());
        s.setInitOrder(1);
        s.setInitParameter("resteasy.scan", "false");
        s.setInitParameter("resteasy.resources",
                 HomeResource.class.getName()
            +","+UsersResource.class.getName()
            +","+LogsResource.class.getName()
            +","+ContractsResource.class.getName()
        );
        managerContextHandler.addServlet(s,"/*");
        contextHandlers.addHandler(managerContextHandler);

        //all requests pass through the following chain of handlers:
        HandlerList serialHandlers = new HandlerList();
        serialHandlers.addHandler(new Firewall());
        serialHandlers.addHandler(new Authenticator());
        serialHandlers.addHandler(contextHandlers);
        server.setHandler(serialHandlers);
    }

    private static void initializeModules() {
        co.gridport.server.jms.Module.initialize();
    }

    private static void startServer() throws Exception {
        server.start();
    }

    public static void restart() {
        new Thread() {
            @Override public void run() {
                try {
                    reloadServerConfig();
                    server.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public static void shutdown() {
        stopServer();

        //close modules
        co.gridport.server.jms.Module.close();
        co.gridport.server.space.Space2.close();

        policyProvider.close();

        log.info("*** SHUT DOWN COMPLETE ***");
        //Info(Thread.getAllStackTraces());
    }

    private static void stopServer() {
        if (server != null)
        {
            log.info("*** Stopping GridPort server");
            try {
                if (server.isRunning()) {
                    server.stop();
                    for(Connector c: server.getConnectors()) {
                        server.removeConnector(c);
                    }
                    policyProvider.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
