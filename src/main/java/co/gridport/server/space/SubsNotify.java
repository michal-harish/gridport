package co.gridport.server.space;

import java.io.DataOutputStream; 
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
//import java.net.URLEncoder;

public class SubsNotify extends Subscription {

	
	private URL url;
	private String email;
	protected long lastActivity;
	
	static public void Subscribe(String pattern, String target) throws SpaceError {		
		String tmp = "notification\\("+target+"\\)";
		SimpleTuple[] s = Space2.READ(tmp);
		if (s.length > 0) for(SimpleTuple T:s) {
			if (T.getData().equals(pattern)) {
				throw new SpaceError(19,"Already subscribed "+pattern+" FOR "+target);
			}
		} 
		tmp = "notification("+target+")";
		new SubsNotify(pattern, target);
		Space2.WRITE(tmp, pattern);
	}
		
	static public void Unsubscribe(String pattern, String target) throws SpaceError {
		String tmp = "notification\\("+target+"\\)";			
		for(SimpleTuple T:Space2.READ(tmp)) {
			if (T.getData().equals(pattern)) {
				try {
					Space2.TAKE(T.getDescriptor(), 1000);
				} catch (InterruptedException e) {
					Space2.log.warn("UNSUBSCROBE FAILED DUE TO INTERRUPTION");
				}
				return;
			}
		}
		throw new SpaceError(18,"Subscription does not exist");
	}
	
	public SubsNotify(String regexpattern, String aTarget) throws SpaceError {
		super(regexpattern, aTarget);		
		primary_observer = new ObserverRead( primary_automaton, pattern );	
		if (target.matches("^https?\\:\\/\\/.*")) {
			try {
				url = new URL(target);
			} catch (MalformedURLException e) {
				throw new SpaceError(000,"Malformed Subscriptino URL");
			}
			setName( url.getFile() );									
		} else {
			email = target;
		}
		
		start();		
	}		
	
	public void execute() throws SpaceError {
		
		Space2.BEGIN();		
		
		String descriptor = null;
		String data = null;
		SimpleTuple T = null;
		try {		
			T = primary_observer.Next(0);				
		} catch(InterruptedException e) {
			this.interrupt();
			Space2.ROLLBACK();
			return;
		}

		descriptor = T.getDescriptor();
		data = T.getData();
		if (descriptor == null || descriptor.equals("")) throw new SpaceError(1,"INVALID TUPLE DESCRIPTOR");			
		lastActivity = System.currentTimeMillis();
		
		//INVOCATION
		if (url != null) {
			//long invoking = Space2.logMs();	
			Space2.log.info("NOTIFY URL "+target);
			try {	
				HttpURLConnection Conn = (HttpURLConnection)url.openConnection();						
				Conn.setDoInput (true);
				Conn.setDoOutput (true);
				Conn.setUseCaches (false);
				//TODO how to detect multipart from urlencoded
				if (data.substring(0,2).equals("--")) {
					String boundary = data.substring(2,data.indexOf("\r"));
					Conn.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);				
				} else {
					Conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				}
				Conn.setRequestProperty("Content-Length", String.valueOf(data.getBytes().length));
				DataOutputStream post = new DataOutputStream (Conn.getOutputStream ());
			    //String content = URLEncoder.encode(descriptor,"UTF-8")+"="+URLEncoder.encode(data,"UTF-8");						    
			    //post.writeBytes(content);					    
				post.writeBytes(data);
			    post.close();
			    //Space2.logMs(invoking, "INVOCATION ");
			    try {
			    	Conn.getInputStream().close();
			    	Conn.disconnect();
			    } catch (IOException e) {			    	
			    	String response_status = e.getMessage() +" "+ Conn.getResponseMessage();
			    	throw new IOException(response_status);
			    }
			    //Space2.logMs(invoking, "EXECUTION ");				    

			} catch (IOException e) {
				//LOG		
				
				throw new SpaceError(16,"TODO RE-QUEUE : "+e.getMessage());
			}
		} else { // send email
			Space2.log.info("NOTIFY EMAIL TO: "+email);
			Space2.log.info("NOTIFY EMAIL SUBJECT: "+descriptor);
			Space2.log.info("NOTIFY EMAIL BODY: "+data);
			
		}		
		
		Space2.COMMIT();

	}
}
