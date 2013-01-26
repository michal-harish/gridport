package co.gridport.server.jms;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.naming.Context;
import javax.naming.NamingException;

abstract public class POSTListener implements MessageListener,javax.jms.ExceptionListener{
	static public String type = "abstract";
	public String destName;
	
	public POSTListener(Context ctx, String destName, String target) throws IOException,MalformedURLException, JMSException, NamingException {
		this.destName = destName;
	}
	abstract protected void close() throws JMSException;	
	abstract protected void unsubscribe() throws JMSException;

}
