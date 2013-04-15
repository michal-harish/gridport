package co.gridport.server.router;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.GridPortServer;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;
import co.gridport.server.jms.ModuleJMS;
import co.gridport.server.utils.Utils;


public class ProxyRequestThread extends Thread {

    static protected Logger log = LoggerFactory.getLogger("request");
    static protected SimpleDateFormat date = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss.SSS zzz");

    protected HttpServletRequest request;
    protected HttpServletResponse response;
    protected RequestContext context;
    protected byte[] body;    
    protected int body_len;
    protected Date received;
    protected long receivedMillisec;
    protected boolean loaded = false;

    protected String merge_duration = "-";
    protected long merge_size = 0;

    protected ArrayList<String> logList = new ArrayList<String>();

    protected List<SubRequest> subrequests = new ArrayList<SubRequest>(); 
    protected List<SubRequest> asyncSubrequests = new ArrayList<SubRequest>();

    public ProxyRequestThread(
        RequestContext context
    ) {
        this.request = context.getRequest();
        this.response = context.getResponse();
        this.context = context;
        receivedMillisec = context.getReceivedTimestampMs();
        received = new Date(receivedMillisec);
    }

    protected void log(String message) {
        logList.add(message);
    }

    protected void flushLog() {
        if (logList.size()>0) {
            String log4 = new String();
            for(String s:logList) {
                log4 += (log4.length()> 0 ? " | " : "")+s;
            }
            logList.clear();
            log.info(log4);
        }
    }

    public List<SubRequest> getAsyncSubrequests() {
        return asyncSubrequests;
    }

    public void notifyAsyncSubrequests()
    {
        synchronized(asyncSubrequests) { 
            asyncSubrequests.notify();
        }
    }

    public String getInfo() {
        return received + ": " + 
            context.getConsumerAddr() + " " + 
            request.getMethod() + " " + 
            request.getRequestURI() + " "  
            + (context.getHost() !=null ? " VIA " + context.getHost() : "") 
            + " (" + ((System.currentTimeMillis() - receivedMillisec)/1000) 
            + "sec) \n";
    }

    final public void run() {
        try {
            log((context.isHttps() ? "SSL " : "") + request.getProtocol() + " " + request.getMethod() + " " + request.getRequestURI() +" FROM " + context.getConsumerAddr() + " VIA " + "/" + context.getHost());

            try {

                log("USING " + context.getPreferredContract().getName() 
                    + (context.getWaitingTime() >0 ? " WAITING " + context.getWaitingTime() : "") 
                );

                execute();

                if (subrequests.size() == 1) {
                    proxyPassthrough(subrequests.get(0));
                } else if (subrequests.size() > 1) {
                    proxyMulticast();
                } else if (asyncSubrequests.size() > 0) {
                    proxyFulfilment();
                }

                joinAsyncSubrequests();

            } catch (IOException e1) {
                serverError(e1);
            } catch (InterruptedException e2) {
                response.setStatus(410); //Gone
                response.setContentLength(-1);
                return;
            }

        } finally {
            complete();
            flushLog();
        }
    }

    protected void execute() throws InterruptedException {
        try {
            //1. initialize all subrequests within client thread
            for(Route E:context.getRoutes()) {
                //TODO ROUTING SECONDARY consider compensation settings for each executed method after finding out failure;
                String URL = E.endpoint + E.uri + E.query_stirng;
                try {
                    E.subrequest = new SubRequestMerge(this , URL, E.async);
                    E.subrequest.base = E.uri;

                } catch (IOException e) {
                    //TODO ROUTING SECONDARY consider compensation settings for each executed method after finding out failure;
                    log.error("ROUTER SUBREQUEST",e);
                }
            }
            //combine all tasks and events into a single list
            List<SubRequest> newSubrequests = new ArrayList<SubRequest>();
            newSubrequests.addAll(this.subrequests);
            newSubrequests.addAll(this.asyncSubrequests);

            //2.stream client request to all subrequests in parallel 
            if (request.getHeader("Content-Type") != null) {
                if (request.getHeader("Content-Length") == null) {
                    log("! No Content-Length for entity " + request.getHeader("Content-Type"));
                } else {
                    int body_size = request.getIntHeader("Content-Length");
                    byte[] buffer = new byte[8192];
                    int read;
                    int read_offset = 0;
                    int zero_read = 0;
                    InputStream is = request.getInputStream();
                    do {
                        int block_size = Math.min(body_size-read_offset, buffer.length);
                        read = is.read(buffer, 0, block_size);
                        for(SubRequest subrequest:newSubrequests)
                        {
                            if (read > 0
                                && !subrequest.conn.getRequestMethod().equals("OPTIONS")
                                && !subrequest.conn.getRequestMethod().equals("DELETE")
                            )
                            {
                                try {
                                    System.err.println("Writing " + new String(buffer));
                                    subrequest.writeOut(buffer, read);
                                } 
                                catch (IOException e) 
                                {
                                    log(e.getMessage());
                                }
                            }
                        }
                        if (read>0) {
                            read_offset += read;
                        } else {
                            if (zero_read++>512) throw new IOException("ClientTrhead.init() too many zero packets int the inputstream");
                        }
                    } while (read>=0 && read_offset < body_size);
                    if (read_offset != body_size)
                    {
                        throw new IOException("Expecting content length: " + body_size+", actually read: " + read_offset);
                    }
                }
            }

            //3. start() all tasks and events to complete within their own thread space
            for(SubRequest subrequest:newSubrequests)
            {
                subrequest.start();
            }
        } catch (Exception e) {
            log.error("ROUTER SUBREQUESTS",e);
        }
    }

    protected void complete() {
        ModuleRouter routerModule = GridPortServer.getModule(ModuleRouter.class);
        ModuleJMS jmsModule = GridPortServer.getModule(ModuleJMS.class);
        if (!Utils.blank(routerModule.logTopic)) {
            if (jmsModule.initialized) {
                HashMap<String,String> log_properties = new HashMap<String,String>();
                log_properties.put("gridport-log-version", "1");
                log_properties.put("gridport-log-date", date.format(System.currentTimeMillis()));
                String log_payload = "gateway="+context.getHost()+"\r\n";
                log_payload += "protocol="+(context.isHttps() ? "https":"http" ) + "\r\n";
                log_payload += "port="+ request.getLocalPort() +"\r\n";
                log_payload += "url=" +  request.getRequestURL()+"\r\n";
                log_payload += "received=" + date.format(received.getTime() ) +"\r\n";
                log_payload += "contract=" + context.getPreferredContract().getName() +"\r\n";
                log_payload += "consumer.ip=" + context.getConsumerAddr() +"\r\n";
                log_payload += "consumer.id=" + context.getUsername() + "\r\n";
                log_payload += "duration=" + String.valueOf( (double) ( System.currentTimeMillis() - received.getTime() ) / 1000 )+"\r\n";
                log_payload += "input="+body_len+"\r\n";
                log_payload += "output="+merge_size+"\r\n";
                log_payload += "volume="+(body_len+merge_size)+"\r\n";
                for(Route E:context.getRoutes()) {
                    log_payload += "endpoint="+E.endpoint+"\r\n";
                }
                try {
                    // week expiration for log messages
                    GridPortServer.getModule(ModuleJMS.class).publish(routerModule.logTopic, log_payload, log_properties,60*60*24*7);
                } catch (Exception e) {
                    log.warn(routerModule.logTopic+" ERROR " + e.getMessage());
                }

            } else {
                log.warn("JMS module not available for sending logs to "+routerModule.logTopic);
            }
        }
    }

    final protected void loadIncomingContentEntity() throws IOException {
        if (loaded) return;
        body_len = 0;
        loaded = true;
        if (request.getHeader("Content-Type") != null) {
            if (request.getHeader("Content-Length") == null) {
                log("! No Content-Length for entity " + request.getHeader("Content-Type"));
            } else {
                int body_size = Integer.valueOf(request.getHeader("Content-Length"));
                body = new byte[body_size];
                int read;
                int zero_read = 0;
                do {
                    read = request.getInputStream().read(
                        body, body_len, Math.min(body_size-body_len,4096)
                    );
                    if (read>0) {
                        body_len += read;
                    } else
                        if (zero_read++>512) {
                            throw new IOException("Too many zero packets in the InputStream");
                        }
                } while (read>=0);

                if (body_len != body.length) {
                    throw new IOException(
                        "Expecting content length: " + body.length+", actually read: " + body_len
                    );
                }
            } 
        }
    }

    private void proxyPassthrough(SubRequest subrequest) {
        try { 
            subrequest.join();
            if (subrequest.error != null) {
                if (subrequest.error != null) {
                    log("= PASSTHROUGH " + subrequest.getURL() + " ERROR " + subrequest.error);
                }
                serverError(subrequest.error);
            } else {
                int content_length = replicateResponseHeaders(subrequest);
                if (subrequest.statusCode == 301
                    || subrequest.statusCode == 302
                ) 
                { //TODO catch 30x headers and replace location
                    String location = response.getHeader("Location");
                    String newloc = location.replaceFirst("https?://"+subrequest.conn.getURL().getHost(), "http" + (context.isHttps() ? "s":"") +"://"+ context.getHost() + subrequest.base);
                    log(subrequest.statusCode + " ! " + location + " >> " + newloc); 
                    response.setHeader("Location",newloc);
                    log.debug("RESPONSE HEADER Location: " + response.getHeader("Location"));
                }
                response.setContentLength(content_length);
                log.debug("RESPONSE HEADER Content-Length: " + response.getHeader("Content-Length"));
                response.setStatus(subrequest.statusCode);

                log.debug("RESPONSE STATUS " + response.getStatus());
                merge_size += serveTaskStream(subrequest, content_length);
                merge_duration = String.valueOf( (double) ( System.currentTimeMillis() - received.getTime() ) / 1000 );
                log("= "+merge_duration+ " PASSTHROUGH " + subrequest.statusCode + " " + subrequest.conn.getURL().toString()+ " RESPONSE LENGTH " + merge_size);
                response.getOutputStream().close();
            }
        } catch (Exception e) {
            //response.setStatus(0);
            log("= PASSTHROUGH FAILED " + subrequest.conn.getRequestMethod() + " "+ subrequest.conn.getURL().toString());
            log.error("PASSTHROUGH FAILED", e);
        }
    }

    private void proxyMulticast() {
        /*  
        String ContentType = null;
        for(SubRequest t: tasks) {
            try {
                t.join();
                if (ContentType == null) { // TODO MERGE consequent contentTypes are not validated 
                    ContentType = t.conn.getContentType();
                    replicateResponseHeaders(t);
                }
                merge_size += t.response_len;
                if (t.error != null) throw t.error;
                log("= MERGE " + t.statusCode + " " + t.conn.getURL().toString()+ " RESPONSE LENGTH " + t.response_len);
            } catch (Exception e) {
                log("= MERGE FAILED " + t.conn.getRequestMethod() + " "+ t.conn.getURL().toString());                       
                return;
            }
        }
        if (ContentType == null)
            throw new IOException("Unknown Content-Type");

        log("MERGE Content-Type = "+ContentType);
        if (ContentType.equals("application/json")) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            long total = merge_size + 2 + (tasks.size()-1); 
            exchange.getResponseHeaders().set("Content-Length",String.valueOf(total));
            exchange.sendResponseHeaders(200, total);
            exchange.getResponseBody().write("[".getBytes());
            int item = 0;
            for(SubRequest t: tasks) {
                if (item>0) exchange.getResponseBody().write(",".getBytes());
                //exchange.getResponseBody().write("{".getBytes());
                exchange.getResponseBody().write(t.response, 0, t.response_len );
                //exchange.getResponseBody().write("}".getBytes());
                item++;
            }
            exchange.getResponseBody().write("]".getBytes());
        } else {    
            //TODO generate timestamp based boundary
            String boundary = "------------Bx0776xdf65d4fgsdf8-";           
            exchange.getResponseHeaders().set("Content-Type", "multiple/" + ContentType +",boundary=" + boundary);
            exchange.getResponseHeaders().set("Content-Length",String.valueOf(merge_size + tasks.size() * boundary.length()));
            exchange.sendResponseHeaders(200, merge_size + tasks.size() * boundary.length());           
            for(SubRequest t: tasks) {
                exchange.getResponseBody().write(boundary.getBytes());
                exchange.getResponseBody().write(t.response, 0, t.response_len );
            }
        }

        merge_duration = String.valueOf( (double) ( System.currentTimeMillis() - received.getTime() ) / 1000 );
        response.getOutputStream().close();
        log("= " + merge_duration + " MERGE COMPLETE " + 200);
        */
    }

    private void proxyFulfilment() {
        //use the first event that has an async status as a response
        int eventStatus = asyncSubrequests.get(0).async_statusCode;
        switch(eventStatus ) {
            default: case 202:
                response.setStatus(eventStatus);
                response.setContentLength(-1);
                log("= EVENT ACCEPTED WITH " + eventStatus);
                break;
            case 301: case 302: case 303: case 304: case 305: case 307:
                response.setHeader("Location", request.getRequestURI());
                response.setStatus(eventStatus);
                response.setContentLength(-1);
                log("= EVENT ACCEPTED WITH REDIRECT "+eventStatus); 
                break;
        }
    }

    private void joinAsyncSubrequests() throws IOException,InterruptedException {
        if (asyncSubrequests.size()>0) {
            List<SubRequest> remainingSubrequests = new ArrayList<SubRequest>();
            while(true) {
                remainingSubrequests.clear();
                synchronized(asyncSubrequests) {
                    Collections.copy(remainingSubrequests, asyncSubrequests);
                }
                int wait_minutes = 1;
                for(SubRequest t: remainingSubrequests) {
                    t.join();
                    synchronized(asyncSubrequests) {
                        asyncSubrequests.remove(t);
                    }
                    if (t.statusCode <400 && t.error == null) {  
                        log("= " + t.runtime + " ASYNC " + t.conn.getRequestMethod()+ " " + t.conn.getURL().toString() +"; HTTP STATUS="+ t.statusCode );                        
                    } else { // 4xx or 5xx or Connection Errors are requeued
                        log("RETRY "+t.conn.getRequestMethod()+ " " + t.conn.getURL().toString()+ "; EVENT EXPECTS 2xx 3xx " + t.async_statusCode+ "; " + (t.error != null ? t.error : t.statusCode + " RETURNED"));
                        flushLog();
                        new SubRequestMerge(
                            this, t.conn.getURL().toString(),String.valueOf(t.async_statusCode)
                        ).start();
                    }
                }
                synchronized(asyncSubrequests) {
                    try {
                        if (asyncSubrequests.size()==0 ) break;
                        else asyncSubrequests.wait(wait_minutes * 60000);
                        wait_minutes++; // each time we wait one minute longer
                    } catch(InterruptedException e) {
                        log("= TODO Compensate for pending Event Subrequests");
                        throw e;
                    }
                }
            }
        }
    }

    private int replicateResponseHeaders(SubRequest t) {
        String[] keys = new String[t.headers.keySet().size()];
        t.headers.keySet().toArray(keys);
        int content_length = -1;
        for ( int i=0; i<keys.length ; i++) {
            String header =  keys[i];
            if (header == null) continue;
            if (header.toLowerCase().equals("transfer-encoding")) continue; 
            if (header.toLowerCase().equals("content-length")) 
            {
                content_length = Integer.valueOf(t.headers.get(header).get(0));
                continue;
            }
            for( int j =0; j<t.headers.get(header).size(); j++) {
                String value = t.headers.get(header).get(j);
                log.debug("RESPONSE HEADER "+ header + ": " + value);
                response.addHeader(header,value);
            }
        }
        return content_length;
    }

    protected int serveTaskStream(SubRequest task, int content_length) throws IOException
    {
        byte[] buffer = new byte[8192];
        int read;
        int zero_read = 0;
        int response_len = 0;
        InputStream in = null;
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(response.getOutputStream());
            in = task.conn.getInputStream();
            //@TODO catch all exceptions caused by 4xx satus codes on getinputstream
        } catch (IOException xxx) {//FileNotFoundException e404) {
            log(xxx.getMessage());
            in = task.conn.getErrorStream();
            if (!(in instanceof InputStream)) {
                return 0;
            }
        }
        do {
            read = in.read(buffer, 0, buffer.length);
            log.debug("SERVE TASK STREAM READ " + read);
            if (read>0) {
                out.write(buffer, 0, read);
                response_len += read;
            } else {
                zero_read++;
                if (zero_read>3) throw new IOException("Too many zero packets inputstream whil serveTaskStream() execution");
            }
        } while (read>=0);
        return response_len;
    }

    protected void serverError(Throwable e) {
        log.error("Server Error", e);
        if (e instanceof java.net.ConnectException) {
            response.setStatus(503);
            response.setContentLength(-1);
        } else {
            response.setStatus(500);
            response.setContentLength(-1);
        }
    }

}
