package co.gridport.server;

import org.eclipse.jetty.server.handler.ContextHandler;

public interface Module {

    ContextHandler register(ConfigProvider config, String contextPath) throws Exception;
    void close();

}
