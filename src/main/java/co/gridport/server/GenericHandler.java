package co.gridport.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.handler.AbstractHandler;

abstract public class GenericHandler extends AbstractHandler {
    static protected SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss.SSS zzz");
    
    final protected byte[] loadIncomingContentEntity(HttpServletRequest request) throws IOException {
        byte[] entity;
        int body_len = 0;
        if (request.getHeader("Content-Type") == null 
            || request.getHeader("Content-Length") == null
        ) {
            throw new IOException("Could not read request content entity - missing content type headers");
        } else {
            int body_size = Integer.valueOf(request.getHeader("Content-Length"));
            entity = new byte[body_size];
            int read;
            int zero_read = 0;
            do {
                read = request.getInputStream().read(
                    entity, body_len, Math.min(body_size-body_len,4096)
                );
                if (read>0) {
                    body_len += read;
                } else
                    if (zero_read++>512) {
                        throw new IOException("Too many zero packets in the InputStream");
                    }
            } while (read>=0);

            if (body_len != entity.length) {
                throw new IOException(
                    "Expecting content length: " + entity.length+", actually read: " + body_len
                );
            }
            return entity;
        }

    }

    protected void serveFile(HttpServletRequest request, HttpServletResponse response, String filename) throws IOException {
        File inFile = new File(filename);
        if (!inFile.isFile()) {
            serveHtmlError(response, 404, "Not Found", "File not found on the server: " + filename);
        } else {

            response.setHeader("Connection", "close");

            //TODO extension to mime type
            if (request.getHeader("If-Modified-Since") != null) {
                String ifmod = request.getHeader("If-Modified-Since");
                try {
                    if (inFile.lastModified() <= dateFormatter.parse(ifmod).getTime()) {
                        response.setStatus(304);
                        response.setContentLength(-1);
                        return;
                    }
                } catch (ParseException e) {
                    throw new IOException("Invalid Request If-Modified-since time format: "+ifmod);
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
                }
            } finally {
                in.close();
            }
        }
    }

    final protected void serveHtmlError(HttpServletResponse response, int statusCode, String title, String message) throws IOException {
        StringWriter writer = new StringWriter();
        IOUtils.copy(this.getClass().getResourceAsStream("/40x.html"), writer);
        String text = writer.toString();
        text = text.replaceAll("\\$status", String.valueOf(statusCode))
           .replaceAll("\\$title", title)
           .replaceAll("\\$message", message);

        response.setHeader("Connection", "close");
        response.setHeader("Content-Length", String.valueOf(text.length()));
        if (response.getHeader("Content-Type") == null) {
            response.setHeader("Content-Type","text/html");
        }

        byte[] b = text.getBytes();
        response.setStatus(statusCode);
        response.setContentLength(b.length);
        response.getOutputStream().write(b);
    }

    final protected void serveText(HttpServletResponse response, int statusCode,String text) throws IOException {
        response.setHeader("Connection", "close");
        response.setHeader("Content-Length", String.valueOf(text.length()));
        if (response.getHeader("Content-Type") == null) {
            response.setHeader("Content-Type","text/plain");
        }
        byte[] b = text.getBytes();
        response.setStatus(statusCode);
        response.setContentLength(b.length);
        response.getOutputStream().write(b);
    }

}
