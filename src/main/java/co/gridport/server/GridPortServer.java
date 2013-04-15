package co.gridport.server;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.handler.Authenticator;
import co.gridport.server.handler.Firewall;
import co.gridport.server.handler.LoadBalancer;
import co.gridport.server.jms.ModuleJMS;
import co.gridport.server.kafka.ModuleKafka;
import co.gridport.server.manager.ModuleManager;
import co.gridport.server.router.ModuleRouter;
import co.gridport.server.utils.Utils;

public class GridPortServer {

    private static Logger log = LoggerFactory.getLogger("server");
    protected static GridPortServer instance;

    public static void main(String[] args) throws InterruptedException {
        instance = new GridPortServer();
        instance.startServer();
        log.info("****************************************************");
        synchronized(instance) {
            instance.wait();
        }
        log.info("****************************************************");
    }

    public static List<Request> getActiveRequests() {
        return instance.activeRequests;
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
                instance.stopServer();
                instance.startServer();
            }
        }.start();
    }

    public String instanceId;
    private Server server;
    private List<Module> modules;
    private HandlerCollection requestHandlers;
    private ConfigProvider config;
    private List<Request> activeRequests;

    private void startServer() {
        try {
            instanceId = "GRIDPORT-"+InetAddress.getLocalHost().getHostName();
            log.info("*** Starting " + instanceId);
            activeRequests = new ArrayList<Request>();
            server = new Server();
            modules = new ArrayList<Module>();
            loadServerConfig();
            server.setGracefulShutdown(1000);
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override public void run() {
                    shutdown();
                }
            });
            initializeHandlers();
            server.start();
        } catch (Exception e) {
            log.error("*** initialization critical failure ",e);
            System.exit(0);
        }
    }


    public void loadServerConfig() throws Exception {

        config = new ConfigProviderSQLite();
 
        server.setThreadPool(new ExecutorThreadPool(Executors.newFixedThreadPool(
            config.has("threads") ? Integer.valueOf(config.get("threads")) : 50
        )));

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
            SslSocketConnector connector2 = new SslSocketConnector(sslContextFactory);
            connector2.setPort(Integer.valueOf(config.get("sslPort")));
            server.addConnector(connector2);
            log.info("*** SSL CONNECTOR PORT " + connector2.getPort());
        }

    }

    private void initializeHandlers() {
        requestHandlers = new HandlerCollection();

        server.setHandler( new HandlerCollection() {{
            setHandlers(new Handler[] {
                new AbstractHandler() {
                    @Override public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
                        activeRequests.add(baseRequest);
                    }
                },
                new HandlerList() {{
                    setHandlers(new Handler[] {
                        new Firewall(config),
                        new Authenticator(config),
                        new LoadBalancer(config),
                        requestHandlers
                    });
                }},
                new AbstractHandler() {
                    @Override public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
                        activeRequests.remove(baseRequest);
                        baseRequest.setAttribute("status", "Completed");
                    }
                }
            });
        }});

        addModule(new ModuleManager(), "/manage");
        //addModule(new ModuleTupleSpace(), "/space");
        addModule(new ModuleKafka(), "/kafka");
        addModule(new ModuleJMS(), "/jms");
        addModule(new ModuleRouter(), "/");
    }

    private void addModule(Module module, String contextPath) {
        try {
            Handler contextHandler = module.register(config, contextPath);
            modules.add(module);
            requestHandlers.addHandler(contextHandler);
        } catch (Exception e) {
            log.error("*** " + module.getClass().getSimpleName() + " initialization failed",e);
        }
    }

    public void shutdown() {
        stopServer();
        config.close();
        log.info("*** SHUT DOWN COMPLETE ***");
        this.notifyAll();
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
                server = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
