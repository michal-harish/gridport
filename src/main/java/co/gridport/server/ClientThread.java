package co.gridport.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.GridPortServer;
import co.gridport.server.domain.Contract;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;
import co.gridport.server.utils.Utils;


abstract public class ClientThread extends Thread {

    static protected Logger log = LoggerFactory.getLogger("request");

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
    protected Contract contract;
    protected String group = null;

    protected ArrayList<String> logList = new ArrayList<String>();

    abstract protected void execute() throws InterruptedException ;
    abstract protected void complete();

    protected List<SubRequest> subrequests = new ArrayList<SubRequest>(); 
    protected List<SubRequest> asyncSubrequests = new ArrayList<SubRequest>();

    public ClientThread(
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

            if (!selectNextAvailableContract()) {
                serveForbidden("No contract available");
            } else try {

                consume(contract);

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
                serveGone();
            }

        } finally {
            complete();
            flushLog();
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

    private void consume(Contract contract) throws InterruptedException {
        log("USING " + contract.getName());
        long sleep_ms = 0;
        while (true) {
            if (sleep_ms>0) {
                log.warn("WAITING FOR SLA SLOT");
                synchronized(contract) {
                    log("Waiting "+sleep_ms+" ms ("+contract.getName()+": FQ = "+contract.getFrequency()+"/"+contract.getIntervalMs()+" ms )");
                }
                Thread.sleep(sleep_ms);
            }
            synchronized(contract) {
                sleep_ms = contract.getIntervalMs() - (System.currentTimeMillis() - contract.getLastRequest());
                if (contract.getFrequency()>0 && contract.getCounter()<contract.getFrequency()) {
                    contract.markCounter();
                } else {
                    if (sleep_ms>0) continue;
                    contract.resetCounter();
                }

                break;
            }
        }
    }

    private boolean selectNextAvailableContract() {
        contract = null; 
        String[] groups = context.getGroups();
        if (groups.length==0 || Utils.blank(groups[0])) group=null; 
        else group = groups[0];
        for(Route E:context.getRoutes()) {   
            for(Contract C:E.contracts) synchronized(C) {
                String found = null;
                if (C.getAuthGroup().length>0) { // contract is only for some auth groups
                    for(String gC:C.getAuthGroup()) {
                        for(String g:groups) if (g.trim().equals(gC.trim())) {
                            found = g.trim(); 
                            break;
                        }
                        if (found!=null) break;
                    }
                    if (found==null) continue;
                }
                if (contract == null) { 
                    contract = C;
                    if (found!=null) group = found;
                } else if (C.getIntervalMs()<contract.getIntervalMs()) {
                    contract = C;
                    if (found!=null) group = found;
                } else if (C.getFrequency()-C.getCounter()>contract.getFrequency()-contract.getCounter()) {
                    contract = C;
                    if (found!=null) group = found;
                }
            }
        }
        return contract != null;
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

    protected void serveFile(String filename) throws IOException {
        File inFile = new File(filename);
        if (!inFile.isFile()) {
            serveNotFound();
        } else {

            response.setHeader("Connection", "close");

            //TODO extension to mime type
            if (request.getHeader("If-Modified-Since") != null) {
                String ifmod = request.getHeader("If-Modified-Since");
                try {
                    if (inFile.lastModified() <= GridPortServer.date.parse(ifmod).getTime()) {
                        response.setStatus(304);
                        response.setContentLength(-1);
                        return;
                    }
                } catch (ParseException e) {
                    log.warn("Invalid If-Modified-since time format: "+ifmod);
                }
            }
            Date lm = new Date(inFile.lastModified());

            response.setHeader("Last-Modified", lm.toString());
            response.setStatus(200);
            response.setContentLength((int) inFile.length());
            DataInputStream in = new DataInputStream(new FileInputStream(inFile));
            DataOutputStream out = new DataOutputStream(response.getOutputStream());
            try {
                byte[] buffer = new byte[32768];
                while (true) { // exception deals catches EOF
                    int size = in.read(buffer);
                    if (size<0) throw new EOFException();
                    out.write(buffer,0,size);
                    merge_size += size;
                }
            } catch (EOFException eof) {
                log("File sent: "+ String.valueOf(inFile.length()));
            } finally {
                in.close();
            }
        }
    }

    protected void serveText(int statusCode,String text) throws IOException {
        response.setHeader("Connection", "close");
        response.setHeader("Content-Length", String.valueOf(text.length()));
        if (response.getHeader("Content-Type") == null) {
            response.setHeader("Content-Type","text/plain");
        }
        byte[] b = text.getBytes();
        response.setStatus(statusCode);
        response.setContentLength(b.length);
        response.getOutputStream().write(b);
        merge_size += b.length;
    }

    protected void serveForbidden(String reason) {
        log(reason);
        response.setStatus(403);
        response.setContentLength(-1);
    }

    protected void serveNotFound() {
        response.setStatus(404);
        response.setContentLength(-1);
    }

    protected void serveGone() {
        synchronized(subrequests) { for(SubRequest T:subrequests) T.interrupt(); }
        synchronized(asyncSubrequests) { for(SubRequest T:asyncSubrequests) T.interrupt(); }
        String info = "Client Thread Killed " + logList.get(1) + " " + logList.get(0);
        log(info);
        response.setStatus(410); //Gone
        response.setContentLength(-1);
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
