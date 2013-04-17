package co.gridport.server.domain;

import org.eclipse.jetty.server.Handler;

import co.gridport.server.config.ConfigProvider;

public interface Module {

    Handler register(ConfigProvider config, String contextPath) throws Exception;
    void close();

}
