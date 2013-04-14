package co.gridport;

import java.io.BufferedInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import javax.ws.rs.core.Application;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.ClientThreadRouter;
import co.gridport.server.ConfigProvider;
import co.gridport.server.ConfigProviderSQLite;
import co.gridport.server.Module;
import co.gridport.server.handler.Authenticator;
import co.gridport.server.handler.Firewall;
import co.gridport.server.handler.RequestHandler;
import co.gridport.server.jms.ModuleJMS;
import co.gridport.server.kafka.ModuleKafka;
import co.gridport.server.manager.ModuleManager;
import co.gridport.server.router.ModuleRouter;
import co.gridport.server.utils.Utils;

public class GridPortServer extends Application {

    private static Logger log = LoggerFactory.getLogger("server");
    public static  SimpleDateFormat date = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss.SSS zzz");
    private static GridPortServer instance;

    public static void main(String[] args) throws UnknownHostException {
        boolean cliEnabled = false;
        if (args.length>0) {
            for(String A:args) {
                if (A.equals("cli")) cliEnabled=true;
            }
        }
        instance = new GridPortServer(cliEnabled);
        instance.run();
    }

    public static String getInstanceId() {
        return instance.instanceId;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T getModule(Class<? extends T> moduleClass) {
        for(Module module:instance.modules)  {
            if (module.getClass().equals(moduleClass)) {
                return (T) module;
            }
        }
        return null;
    }

    public static void restart() {
        new Thread() {
            @Override public void run() {
                try {
                    instance.stopServer();
                    instance.reloadServerConfig();
                    instance.initializeModules();
                    instance.startServer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
    public String instanceId;
    private boolean cliEnabled = false;
    private Server server;
    private List<Module> modules = new ArrayList<Module>();
    private ContextHandlerCollection contextHandlers;
    private ConfigProvider config;

    public GridPortServer(Boolean cliEnabled) throws UnknownHostException {

        this.cliEnabled = cliEnabled;
        instanceId = "GRIDPORT-"+InetAddress.getLocalHost().getHostName();
        log.info("*** " + instanceId);

    }

    public void run() {
        try {
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

    private void createServer() {
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

    public void reloadServerConfig() throws Exception {
        stopServer();

        config = new ConfigProviderSQLite();

        if (config.has("router.log")) ClientThreadRouter.logTopic = config.get("router.log");

        if (config.has("keyStoreFile") && !Utils.blank(config.get("keyStoreFile"))) {
            System.setProperty("javax.net.ssl.trustStore", config.get("keyStoreFile"));
            System.setProperty("javax.net.ssl.trustStorePassword", config.get("keyStorePass"));
            log.info("*** TRUST KEYSTORE " +  config.get("keyStoreFile"));
        }

        if (config.has("httpPort"))
        {
            SelectChannelConnector connector1 = new SelectChannelConnector();
            connector1.setPort(Integer.valueOf(config.get("httpPort")));
            connector1.setMaxIdleTime(30000);
            server.addConnector(connector1);
            log.info("*** HTTP CONNECTOR PORT " + connector1.getPort());
        }
        if (config.has("sslPort")) {
            SslContextFactory sslContextFactory = new SslContextFactory(config.get("keyStoreFile"));
            sslContextFactory.setKeyStorePassword(config.get("keyStorePass"));
            //sslContextFactory.setTrustStore(TRUSTSTORE_LOCATION);
            //sslContextFactory.setTrustStorePassword(TRUSTSTORE_PASS);
            //sslContextFactory.setNeedClientAuth(true);

            SslSocketConnector connector2 = new SslSocketConnector(sslContextFactory);
            connector2.setPort(Integer.valueOf(config.get("sslPort")));
            server.addConnector(connector2);
            log.info("*** SSL CONNECTOR PORT " + connector2.getPort());
        }

        //all requests pass through the following chain of handlers:
        HandlerList serialHandlers = new HandlerList();
        serialHandlers.addHandler(new Firewall(config));
        serialHandlers.addHandler(new Authenticator(config));
        contextHandlers = new ContextHandlerCollection();
        serialHandlers.addHandler(contextHandlers);
        server.setHandler(serialHandlers);
    }

    private void initializeModules() {
        addModule(new ModuleManager(), "/manage");
        //addModule(new ModuleTupleSpace(), "/space");
        addModule(new ModuleKafka(), "/kafka");
        addModule(new ModuleJMS(), "/jms");
        addModule(new ModuleRouter(), "/");
    }

    private void addModule(Module module, String contextPath) {
        try {
            ContextHandler contextHandler = module.register(config, contextPath);
            modules.add(module);
            contextHandlers.addHandler(contextHandler);
        } catch (Exception e) {
            log.error("*** " + module.getClass().getSimpleName() + " initialization failed",e);
        }
    }

    private void startServer() throws Exception {
        server.start();
    }

    public void shutdown() {
        stopServer();
        config.close();
        log.info("*** SHUT DOWN COMPLETE ***");
    }

    private void stopServer() {
        if (server != null)
        {
            log.info("*** Stopping GridPort server");
            try {
                if (server.isRunning()) {
                    server.stop();
                }
                if (server.getConnectors() != null) {
                    for(Connector c: server.getConnectors().clone()) {
                        server.removeConnector(c);
                    }
                }

                for(Module module:modules) {
                    module.close();
                }
                modules.clear();

                if (config !=null) config.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
