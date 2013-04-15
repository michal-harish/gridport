package co.gridport.server.jms;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

import javax.jms.JMSException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.GenericHandler;
import co.gridport.server.GridPortServer;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;
import co.gridport.server.utils.Utils;

public class PubSubHandler extends GenericHandler {

    static protected Logger log = LoggerFactory.getLogger("mod_jms");
    private String type;

    public PubSubHandler(String type) {
        this.type = type;
    }

    @Override
    public void handle(String target, Request baseRequest,
            HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        baseRequest.setAttribute("status", "Serving JMS " + type);
        RequestContext context = (RequestContext) request.getAttribute("context");
        ModuleJMS jmsModule = GridPortServer.getModule(ModuleJMS.class);

        Route E = context.getRoutes().get(0);
        log.debug("Router context: " + E.context);
        String destType = type.toLowerCase().substring(0,type.length()-1);

        //list all destinations
        String destName = target.substring(1).replaceAll("/", ".");
        if (Utils.blank(destName)) {
            String reply = new String();
            List<String> list = jmsModule.listDestinations(type);
            if (list.size() == 0) {
                reply = "no data";
            } else {
                for(String item:list) {
                    reply += item+"\r\n";
                }
            }
            this.serveText(response,200,reply);
            baseRequest.setHandled(true);
            return;
        }
        String destination = destType+"://"+destName;

        //list subscriptions and available methods
        if (request.getMethod().equals("OPTIONS")) {
            log.debug("JMS/1.1 LIST OPTIONS FOR "+destination);
            String[] list = jmsModule.listSubscribers(destination);
            String reply = new String();
            for(String item:list) {
                reply += item+"\r\n";
            }
            this.serveText(response,200,reply);
            baseRequest.setHandled(true);
            return;
        }

        //list subscriptions and available methods
        if (request.getMethod().equals("GET")) {
            log.debug("JMS/1.1 LIST "+destination);
            try {
                String content = "";
                for(String receiver: jmsModule.listSubscribers(destination))
                {
                    content += receiver + "\n";
                }
                response.setHeader("Content-type", "text/plain");
                serveText(response,200, content);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                serveHtmlError(response,500, "Server Error", e.getMessage());
            }
            baseRequest.setHandled(true);
            return;
        }

        //subscribe
        if (request.getMethod().equals("PUT")) {
            String payload = new String(loadIncomingContentEntity(request));
            String error = jmsModule.addSubscriber(destination, payload);
            try {
                if (error == null) {
                    log.debug("JMS/1.1 SUBSCRIBED "+payload +" TO "+destination);
                    response.setStatus(200);
                    response.setContentLength(-1);
                } else {
                    log.debug("JMS/1.1 FAILED TO SUBSCRIBE "+payload +" TO "+destination+" - " + error);
                    serveHtmlError(response,400,"Bad Request",error);
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                serveHtmlError(response,500, "Server Error", e.getMessage());
            }
            baseRequest.setHandled(true);
            return;
        }

        //unsubscribe
        if (request.getMethod().equals("DELETE")) {
            String payload = new String(loadIncomingContentEntity(request));
            String error = jmsModule.removeSubscriber(destination, payload);
            try {
                if (error == null) {
                    log.debug("JMS/1.1 UNSUBSCRIBED "+payload +" FROM "+destination);
                    response.setStatus(200);
                    response.setContentLength(-1);
                } else {
                    log.debug("JMS/1.1 FAILED TO UNSUBSCRIBE "+payload +" FROM  "+destination);
                    serveHtmlError(response,400,"Bad Request",error);
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                serveHtmlError(response,500, "Server Error", e.getMessage());
            }
            baseRequest.setHandled(true);
            return;
        }

        //publish 
        if (request.getMethod().equals("POST")) {
            HashMap<String,String> properties = new HashMap<String,String>();
            for (Enumeration<String> headers = request.getHeaderNames() ; 
                headers.hasMoreElements() ;
            ) {
                String header = headers.nextElement();
                for(Enumeration<String> values = request.getHeaders(header); 
                    values.hasMoreElements(); 
                ) {
                    String value = values.nextElement();
                    properties.put(header, value);
                }
            }
            try {
                String payload = new String(loadIncomingContentEntity(request));
                try {
                    String messageID = jmsModule.publish(destination, payload, properties,0);
                    log.debug("JMS/1.1 PUBLISH to "+destination);
                    log.debug(messageID);
                    //send accepted + messageId
                    serveText(response,202, messageID);
                } catch (JMSException e) {
                    log.error("JMS/1.1 FAILED PUBLISH to "+destination, e);
                    serveHtmlError(response,400, "Bad Request", e.getMessage());
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                serveHtmlError(response,500, "Server Error", e.getMessage());
            }
            baseRequest.setHandled(true);
            return;
        }

        //else unsupported method
        response.setStatus(405);
        response.setContentLength(-1);

        baseRequest.setHandled(true);
    }

}
