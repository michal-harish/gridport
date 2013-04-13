package co.gridport.server.kafka;

import java.util.HashMap;
import java.util.Map;

import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

import co.gridport.server.utils.Crypt;

public class ContextHandler extends ServletContextHandler {

    static private Map<String,ClusterInfo> clusters = new HashMap<String,ClusterInfo>();

    public static ClusterInfo getClusterInfo(String connectionString) {
        String id = Crypt.md5(connectionString);
        if (!clusters.containsKey(id)) {
            clusters.put(id, new ClusterInfo(connectionString));
        }
        return clusters.get(id);
    }

    public ContextHandler(String contextPath) {
        super(ServletContextHandler.NO_SESSIONS);
        setContextPath(contextPath);
        ServletHolder s = new ServletHolder(new HttpServletDispatcher());
        s.setInitOrder(1);
        s.setInitParameter("resteasy.scan", "false");
        s.setInitParameter("resteasy.resources",
             ClusterInfoResource.class.getName()
            //+","+UsersResource.class.getName()
        );
        addServlet(s,"/*");
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
