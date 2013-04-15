package co.gridport.server.router;

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.domain.RequestContext;
import co.gridport.server.space.ClientThreadSpace;

public class ProxyRequestHandler extends AbstractHandler {
    static private Logger log = LoggerFactory.getLogger("request");

    static private ArrayList<ProxyRequestThread> threads = new ArrayList<ProxyRequestThread>();

    public void handle(String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException, ServletException
    {
        RequestContext context = (RequestContext) request.getAttribute("context");
        baseRequest.setAttribute("status", "Routing");
        ProxyRequestThread thread;
        if (context.getRoutes().get(0).endpoint.equals("module://space")) {
            log.info("GridPortHandler -> ClientThreadSpace " + request.getRequestURI());
            thread = new ClientThreadSpace(context);
            baseRequest.setAttribute("status", "Serving Space");
        } else {
            log.info("GridPortHandler -> ClientThreadRouter " + request.getRequestURI());
            baseRequest.setAttribute("status", "Serving Proxy");
            thread = new ProxyRequestThread(context);
        }

        synchronized(threads) {
            thread.setName("GridPort Client Request");
            threads.add(thread);
        }

        thread.start();
        try {
            thread.join();
            baseRequest.setHandled(true);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        } finally {
            synchronized(threads) {
                threads.remove(thread);
            }
        }
    }

    public static void notifyEventThreads() {
        synchronized(threads) {
            for(ProxyRequestThread T:threads) {
                T.notifyAsyncSubrequests();
            }
        }
    }

}

