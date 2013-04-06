package co.gridport.server.manager;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Properties;

import javax.ws.rs.core.Response;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeConstants;

public abstract class Resource extends VelocityContext {

    static  {
        Properties p = new Properties();
        p.setProperty("resource.loader", "file");
        p.setProperty("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        p.setProperty( RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.Log4JLogChute" );
        p.setProperty("runtime.log.logsystem.log4j.logger", "velocity");
        Velocity.init(p);
    }

    protected Response view(String templateName) {
        Template template = Velocity.getTemplate(templateName);
        StringWriter sw = new StringWriter();
        template.merge( this, sw);
        return Response.status(200).entity(sw).build();
    }


    @Override
    public Object get(String key) {
        try {
            if (key.equals("this")) {
                return this;
            } else try {
                Field f = this.getClass().getDeclaredField(key);
                return f.get(this);
            } catch (NoSuchFieldException e) {
                return super.get(key);
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

}
