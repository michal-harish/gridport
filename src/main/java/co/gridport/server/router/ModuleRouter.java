package co.gridport.server.router;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.ConfigProvider;
import co.gridport.server.Module;

public class ModuleRouter implements Module {

    static private Logger log = LoggerFactory.getLogger("server");

    protected String logTopic = null; //settings/router.log

    @Override
    public ContextHandler register(ConfigProvider config, String contextPath) throws Exception {

        log.info("Registering module://router at context " + contextPath);

        if (config.has("router.log")) {
            logTopic = config.get("router.log");
        }

        return new ContextHandler(contextPath) {{
            setHandler(new ProxyRequestHandler());
        }};
    }

    @Override
    public void close() {}

}
