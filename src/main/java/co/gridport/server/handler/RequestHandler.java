package co.gridport.server.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.domain.RequestContext;
import co.gridport.server.router.ClientThread;
import co.gridport.server.router.ClientThreadRouter;
import co.gridport.server.router.SubRequest;
import co.gridport.server.space.ClientThreadSpace;

public class RequestHandler extends AbstractHandler {
    static private Logger log = LoggerFactory.getLogger("request");

    static private ArrayList<ClientThread> threads = new ArrayList<ClientThread>();

    public void handle(String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException, ServletException
    {
        RequestContext context = (RequestContext) request.getAttribute("context");
        baseRequest.setAttribute("status", "Routing");
        ClientThread thread;
        if (context.getRoutes().get(0).endpoint.equals("module://space")) {
            log.info("GridPortHandler -> ClientThreadSpace " + request.getRequestURI());
            thread = new ClientThreadSpace(context);
            baseRequest.setAttribute("status", "Serving Space");
        } else {
            log.info("GridPortHandler -> ClientThreadRouter " + request.getRequestURI());
            baseRequest.setAttribute("status", "Serving Proxy");
            thread = new ClientThreadRouter(context);
        }

        synchronized(threads) {
            thread.setName("GridPort Client Request");
            threads.add(thread);
        }

        thread.start();
        try {
            thread.join(); //TODO investigate the SelectChannel connector thread context
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
            for(ClientThread T:threads) {
                T.notifyAsyncSubrequests();
            }
        }
    }

    public static List<String> getActiveThreadsInfo() {
        List<String> result = new ArrayList<String>();
        synchronized (threads) {
            for(ClientThread T:RequestHandler.threads) { 
                result.add (T.getInfo());
                if (T instanceof ClientThreadRouter) {
                    ClientThreadRouter TR = (ClientThreadRouter) T;
                    synchronized (TR.getAsyncSubrequests()) 
                    {
                        for(SubRequest S:TR.getAsyncSubrequests()) {
                            result.add(" + EVENT " + S.getRequestMethod() + " " +S.getURL() + " ; "+ (S.error!=null ? S.error : "") +"\n");
                        }
                    }
                }
            }
        }
        return result;
    }

}

