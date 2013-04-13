package co.gridport.server.router;

import co.gridport.server.handler.RequestHandler;

public class ContextHandler extends org.eclipse.jetty.server.handler.ContextHandler {

    public ContextHandler(String contextPath) {
        super(contextPath);
        setHandler(new RequestHandler());
    }
}
