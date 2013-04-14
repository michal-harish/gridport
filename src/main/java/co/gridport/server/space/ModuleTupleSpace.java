package co.gridport.server.space;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.ConfigProvider;
import co.gridport.server.Module;
import co.gridport.server.domain.Endpoint;
import co.gridport.server.handler.RequestHandler;

public class ModuleTupleSpace implements Module {

    static private Logger log = LoggerFactory.getLogger("server");
    private ContextHandler contextHandler;

    @Override
    public ContextHandler register(ConfigProvider config, String contextPath) throws Exception {

        Endpoint defaultEndpoint = config.getEndpointByTargetUrl("module://space");
        if (defaultEndpoint == null) defaultEndpoint = config.newEndpoint();
        defaultEndpoint.setGatewayHost("");
        defaultEndpoint.setSsl(null);
        defaultEndpoint.setHttpMethod("POST GET OPTIONS MOVE PUT");
        defaultEndpoint.setUriBase(contextPath.replaceFirst("/?$",  "")+"/*");

        Space2.initialize(MediumMemory.class);

        log.info("Registering module://space at context " + contextPath);
        contextHandler = new ContextHandler(contextPath);
        contextHandler.setHandler(new RequestHandler());
        return contextHandler;
    }

    @Override
    public void close() {
        Space2.close();
    }

}
