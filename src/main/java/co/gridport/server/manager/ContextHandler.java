package co.gridport.server.manager;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

public class ContextHandler extends ServletContextHandler {

    public ContextHandler(String contextPath) {
        super(ServletContextHandler.NO_SESSIONS);
        setContextPath(contextPath);
        ServletHolder s = new ServletHolder(new HttpServletDispatcher());
        s.setInitOrder(1);
        s.setInitParameter("resteasy.scan", "false");
        s.setInitParameter("resteasy.resources",
                 HomeResource.class.getName()
            +","+UsersResource.class.getName()
            +","+LogsResource.class.getName()
            +","+ContractsResource.class.getName()
            +","+EndpointsResource.class.getName()
        );
        addServlet(s,"/*");
    }

}
