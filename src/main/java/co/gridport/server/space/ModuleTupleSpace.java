package co.gridport.server.space;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.config.ConfigProvider;
import co.gridport.server.domain.Endpoint;
import co.gridport.server.domain.Module;

public class ModuleTupleSpace implements Module {

    static private Logger log = LoggerFactory.getLogger("server");

    @Override
    public ContextHandler register(ConfigProvider config, String contextPath) throws Exception {

        Endpoint defaultEndpoint = config.getEndpointByTargetUrl("module://space");
        if (defaultEndpoint == null) defaultEndpoint = config.newEndpoint("module://space");
        defaultEndpoint.setHttpMethod("POST GET OPTIONS MOVE PUT");
        defaultEndpoint.setUriBase(contextPath.replaceFirst("/?$",  "")+"/*");
        config.updateEndpoint(defaultEndpoint);

        Space2.initialize(MediumMemory.class);

        log.info("Registering module://space at context " + contextPath); 
        return new ContextHandler(contextPath) {{
            //TODO setHandler(new TupleRequestHandler()); 
        }};
    }

    @Override
    public void close() {
        Space2.close();
    }

}
