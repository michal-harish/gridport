package co.gridport.server;

import org.eclipse.jetty.server.Handler;

public interface Module {

    Handler register(ConfigProvider config, String contextPath) throws Exception;
    void close();

}
