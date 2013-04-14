package co.gridport.server.router;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.ConfigProvider;
import co.gridport.server.Module;
import co.gridport.server.handler.RequestHandler;

public class ModuleRouter implements Module {

    static private Logger log = LoggerFactory.getLogger("server");
    private ContextHandler contextHandler;

    @Override
    public ContextHandler register(ConfigProvider config, String contextPath) throws Exception {

        log.info("Registering module://router at context " + contextPath);
        contextHandler = new ContextHandler(contextPath);
        contextHandler.setHandler(new RequestHandler());
        return contextHandler;
    }

    @Override
    public void close() {}

}
