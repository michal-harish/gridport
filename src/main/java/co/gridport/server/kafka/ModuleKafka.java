package co.gridport.server.kafka;

import java.util.HashMap;
import java.util.Map;

import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.Crypt;
import co.gridport.server.Utils;
import co.gridport.server.config.ConfigProvider;
import co.gridport.server.domain.Endpoint;

public class ModuleKafka implements co.gridport.server.domain.Module {

    static private Logger log = LoggerFactory.getLogger("server");

    static private Map<String,ClusterInfo> clusters = new HashMap<String,ClusterInfo>();

    private ServletContextHandler contextHandler;

    @Override
    public ContextHandler register(ConfigProvider config, String contextPath) throws Exception {

        Endpoint defaultEndpoint = config.getEndpointByTargetUrl("module://kafka");
        if (defaultEndpoint == null) defaultEndpoint = config.newEndpoint("module://kafka");
        defaultEndpoint.setHttpMethod("GET POST");
        defaultEndpoint.setUriBase(contextPath.replaceFirst("/?$",  "")+"/*");
        config.updateEndpoint(defaultEndpoint);

        log.info("Registering module://kafka at context " + contextPath);
        contextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        contextHandler.setContextPath(contextPath);

        ServletHolder s = new ServletHolder(new HttpServletDispatcher());
        s.setInitOrder(1);
        s.setInitParameter("resteasy.providers", "org.jboss.resteasy.plugins.providers.jackson.ResteasyJacksonProvider");
        s.setInitParameter("resteasy.resources", Utils.scanRestEasyResources("co.gridport.server.kafka"));
        contextHandler.addServlet(s,"/*");
        return contextHandler;

    }

    @Override
    public void close() {
        for(ClusterInfo cluster: clusters.values()) {
            cluster.close();
        }
        clusters.clear();
    }

    public static ClusterInfo getClusterInfo(String connectionString) {
        String id = Crypt.md5(connectionString);
        if (!clusters.containsKey(id)) {
            clusters.put(id, new ClusterInfo(connectionString));
        }
        return clusters.get(id);
    }

    static class StringSerializer implements ZkSerializer {

        public StringSerializer() {}
        public Object deserialize(byte[] data) throws ZkMarshallingError {
            if (data == null) return null;
            return new String(data);
        }

        public byte[] serialize(Object data) throws ZkMarshallingError {
            return data.toString().getBytes();
        }
    }

}
