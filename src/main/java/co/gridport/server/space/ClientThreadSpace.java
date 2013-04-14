package co.gridport.server.space;

import java.io.IOException;
import java.io.StringWriter;

import org.json.JSONWriter;

import co.gridport.server.domain.RequestContext;
import co.gridport.server.router.ClientThread;

public class ClientThreadSpace extends ClientThread {

    public ClientThreadSpace(RequestContext context) {
        super(context);
    }
    protected void complete() {
        Space2.close();
    }

    public void execute() throws InterruptedException {
        //load incoming request
        try {
            loadIncomingContentEntity();
        } catch (IOException e1) {
            log.error(e1.getMessage(), e1);
            return;
        }

        try {
            // space must be unaware of the http wrapper
            // but the thread context must be kept for the transaction isolation
            // also how can the sequence of commands be kept in context of a http request
            if (context.getQueryString().equals("")) throw new SpaceError(1,"No tuple name given in the query string");
            //Space2.initialize(MediumMemory.class);
            Space2.initialize(MediumSQLite.class);
            if (request.getMethod().equals("POST")) { // WRITE
                String data = new String(body);
                log("SPACE WRITE " + context.getQueryString()+" "+body_len+" Bytes");
                Space2.BEGIN();
                Space2.WRITE(context.getQueryString(), data);
                response.setStatus(202);
                response.setContentLength(-1);
                Space2.COMMIT();
            } else if (request.getMethod().equals("MOVE")) { //TAKE
                log("SPACE TAKE " + context.getQueryString());
                Space2.BEGIN();
                SimpleTuple T = Space2.TAKE(context.getQueryString(),10000); //give up after second
                //exchange.getResponseHeaders().set("Content-Type", "data");
                try {
                    serveText(200,T.getData());
                    Space2.COMMIT();
                } catch (IOException e) {
                    log.error("SPACE2 TAKE",e);
                }
            } else if (request.getMethod().equals("GET")) { //READ - topmost match
                log("SPACE READ " + context.getQueryString());
                SimpleTuple T = Space2.READ(context.getQueryString(),10000); //give up after second
                //exchange.getResponseHeaders().set("Content-Type", "data");
                try {
                    serveText(200,T.getData());
                } catch (IOException e) {
                    log.error("SPACE2 READ",e);
                }        
            } else if (request.getMethod().equals("PUT")) { //NOTIFY (either url or email address)
                String target = new String(body);
                log("SPACE NOTIFY " + context.getQueryString() + " TO " + target);
                SubsNotify.Subscribe(context.getQueryString(),target);
                response.setStatus(202);
                response.setContentLength(-1);
            } else if (request.getMethod().equals("DELETE")) { //DONT'T NOTIFY (either url or email address)
                String target = new String(body);
                log("SPACE DON'T NOTIFY" + context.getQueryString() + " FROM " + target);
                SubsNotify.Unsubscribe(context.getQueryString(),target);    
                response.setStatus(202);
                response.setContentLength(-1);
            } else if (request.getMethod().equals("OPTIONS")) { //READ[]
                byte type = 0; // json
                if (request.getHeader("Accept") != null) {
                    String Accept = request.getHeader("Accept");
                    log("Accept: "+Accept);
                    if (Accept.indexOf("/xml",0)>0) type = 1;//xml
                }
                log("SPACE READ[] " + context.getQueryString());
                Space2.BEGIN();
                SimpleTuple[] tuples = Space2.READ(context.getQueryString());
                String data = null;
                switch(type) {
                    case 0: //stream the json;
                        StringWriter writer = new StringWriter(0);
                        try {
                            JSONWriter json = new JSONWriter(writer);
                            json.array();
                            for(SimpleTuple T:tuples) json.value(T.getData());
                            json.endArray();
                            writer.close();
                            data = writer.toString();
                            response.setHeader("Content-Type", "application/json");
                        } catch (Exception e) {
                            throw new SpaceError(000,e.getMessage());
                        }
                    break;
                    case 1://stream the xml;
                        data = "<?xml version=\"1.0\"?>\r\n";
                        data +="<tuples>\r\n";
                        for(SimpleTuple T:tuples) data += "<tuple id=\""+T.getDescriptor()+"\"><![CDATA["+(T.getData())+"]]></tuple>\r\n";
                        data +="</tuples>\r\n";
                        response.setHeader("Content-Type", "application/xml");
                        break;
                }

                try {
                    serveText(200,data);
                    Space2.COMMIT();
                } catch (IOException e) {
                    log.error("SPACE2 READ[]",e);
                }
                Space2.COMMIT();
            } 
            //transform http request to space request
            //hand over to the space thread
            //and wait for signal from that thread to process the space response into http response. 
        } catch(SpaceError e){    
            if (e.getCode()==0) {
                response.setStatus(500);
                response.setContentLength(-1);
                log.error(e.getMessage(), e);
            } else {
                log("ERROR/SPACE CODE "+e.getCode()+" "+e.getMessage());
                if (e.getCode()==1) {
                    response.setStatus(404);
                    response.setContentLength(-1);
                }
                else {
                    response.setStatus(400);
                    response.setContentLength(-1);
                }
            }
        }
    }
}

