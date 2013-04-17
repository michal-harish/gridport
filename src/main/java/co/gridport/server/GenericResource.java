package co.gridport.server;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Properties;

import javax.ws.rs.core.Response;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.tools.generic.DateTool;

public class GenericResource extends VelocityContext {

    public DateTool date = new DateTool();
    public Long now = System.currentTimeMillis();
    
    static  {
        Properties p = new Properties();
        p.setProperty("resource.loader", "file");
        p.setProperty("velocimacro.library", "manage/macros.vm");
        p.setProperty("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        p.setProperty( RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.Log4JLogChute" );
        p.setProperty("runtime.log.logsystem.log4j.logger", "velocity");
        Velocity.init(p);
    }

    public Response view(String templateName) {
        Template template = Velocity.getTemplate(templateName);
        StringWriter sw = new StringWriter();
        template.merge(this, sw);
        return Response.status(200).entity(sw.toString()).build();
    }

    @Override
    public Object get(String key) {
        try {
            if (key.equals("this")) {
                return this;
            } else try {
                Field f = this.getClass().getField(key);
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
