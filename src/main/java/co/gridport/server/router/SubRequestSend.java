package co.gridport.server.router;

import java.io.IOException;
import java.net.MalformedURLException;

import joptsimple.internal.Strings;

public class SubRequestSend extends SubRequest {
    private String body = null;
    public SubRequestSend(ProxyRequestThread t, String url, String method, String entity) throws MalformedURLException, IOException {
        super(t, url, null);
        conn.setRequestMethod(method);
        conn.setRequestProperty("X-forwarded-host", income.context.getHost() );
        conn.setDoInput(true);
        body = entity;
    }
    public SubRequestSend(ProxyRequestThread t, String url, String method) throws MalformedURLException, IOException {
        super(t, url, null);
        conn.setRequestMethod(method);
        conn.setDoInput(true);

    }
    protected void execute() throws IOException {
        if (!Strings.isNullOrEmpty(body)) {
            writeOut(body.getBytes(), body.getBytes().length);
        } 
    }
}
