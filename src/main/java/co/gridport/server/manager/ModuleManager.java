package co.gridport.server.manager;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.Utils;
import co.gridport.server.config.ConfigProvider;
import co.gridport.server.domain.Endpoint;
import co.gridport.server.domain.Module;

public class ModuleManager implements Module {

    private static Logger log = LoggerFactory.getLogger("server");

    private ServletContextHandler contextHandler;

    public ContextHandler register(final ConfigProvider config, String contextPath) throws Exception {

        //update endpoint policies
        Endpoint defaultEndpoint = config.getEndpointByTargetUrl("module://manager");
        if (defaultEndpoint == null) defaultEndpoint = config.newEndpoint("module://manager");
        defaultEndpoint.setHttpMethod("GET POST DELETE");
        defaultEndpoint.setUriBase(contextPath.replaceFirst("/?$",  "")+"/*");
        config.updateEndpoint(defaultEndpoint);

        log.info("Registering module://manager at context " + contextPath);
        contextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        contextHandler.setContextPath(contextPath);
        ServletHolder s = new ServletHolder(new HttpServletDispatcher() {
            private static final long serialVersionUID = 1L;
            public void init(ServletConfig servletConfig) throws ServletException {
                super.init(servletConfig);
                servletContainerDispatcher.getDispatcher().getDefaultContextObjects().put(ConfigProvider.class, config);
            }
        });
        s.setInitOrder(1);
        s.setInitParameter("resteasy.providers", "org.jboss.resteasy.plugins.providers.jackson.ResteasyJacksonProvider");
        s.setInitParameter("resteasy.resources", Utils.scanRestEasyResources("co.gridport.server.manager"));
        contextHandler.addServlet(s,"/*");
        return contextHandler;

    }

    @Override
    public void close() {}

}
