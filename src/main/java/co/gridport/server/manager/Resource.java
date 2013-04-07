package co.gridport.server.manager;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeConstants;

import co.gridport.server.domain.RequestContext;

public abstract class Resource extends VelocityContext {

    @Context HttpServletRequest request;
    @Context UriInfo uriInfo;

    public String getHomeUrl() {
        return uriInfo.getBaseUriBuilder().path(HomeResource.class).build().toString();
    }
    public String getLogsUrl() {
        return uriInfo.getBaseUriBuilder().path(LogsResource.class).build().toString();
    }
    public String getRestartUrl() {
        try {
            return uriInfo.getBaseUriBuilder().path(HomeResource.class).path(HomeResource.class.getMethod("restart")).build().toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } 
    }
    public String getUsersUrl() {
        return uriInfo.getBaseUriBuilder().path(HomeResource.class).path("/users").build().toString();
    }
    public String getCurrentUser() {
        RequestContext context = (RequestContext) request.getAttribute("context");
        return context.getUsername();
    }

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
