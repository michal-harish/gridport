
package co.gridport.server;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import co.gridport.GridPortServer;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;
import co.gridport.server.handler.RequestHandler;
import co.gridport.server.space.Space2;
import co.gridport.server.space.Subscription;
import co.gridport.server.utils.Utils;


public class ClientThreadManager extends ClientThread {
	
	public ClientThreadManager(RequestContext context) {
		super(context);
	}
	
	protected void complete() {
		
	}
	
	public void execute() throws InterruptedException {	
		/*
		//load incoming request
		try {
			loadIncomingContentEntity();
		} catch (IOException e1) {
			log.error(e1.getMessage(), e1);
			return;
		}	
			
		//create json string 			
		try {

			//OutputStreamWriter writer = new OutputStreamWriter(manager.conn.getOutputStream());
			StringWriter writer = new StringWriter(0); 								
			JSONWriter json = new JSONWriter(writer);
			Statement sql = GridPortServer.policydb.createStatement();	
			try {
				json.object();	
					json.key("processes");
					json.array();					
					
					for(String threadInfo: ProxyHandler.getActiveThreadsInfo()) {				
						json.value(threadInfo);						
					}

					synchronized(Space2.subs) {
						for(Subscription T:Space2.subs) {	
							json.value("SPACE SUBSCRIPTION " + T.pattern + " TO " + T.target + " "+ ((System.currentTimeMillis() - T.started )/1000) + "sec) \n");
						}
					}
					json.endArray();
					//////////////////////////////////////////////////////////////////////////////////////////////////						
					json.key("users");
					json.array();
					String qry = "SELECT * FROM users";
					ResultSet rs = sql.executeQuery(qry);	
					while (rs.next()) {
						json.object();
						json.key("username");
						json.value(rs.getString("username"));
						json.key("groups");
						json.value(rs.getString("groups"));
						json.endObject();
					}
					json.endArray();
					sql.close();
					//////////////////////////////////////////////////////////////////////////////////////////////////
					
					//fire OPTIONS to all endpoints who implement it 
					qry = "SELECT * FROM endpoints WHERE uri_base IS NOT NULL AND http_method LIKE '%OPTIONS%' ORDER BY service_endpoint";
					rs = sql.executeQuery(qry);			
					while (rs.next()) {
						try {								
							String location = ( "http"+(rs.getBoolean("ssl") ? "s" : "") +"://"+ (Utils.blank(rs.getString("gateway_host")) ? context.getHost() : rs.getString("gateway_host")));
							if (!Utils.blank(rs.getString("uri_base"))) location += rs.getString("uri_base");							
							SubRequest t = new SubRequestSend(this,rs.getString("service_endpoint"),"OPTIONS");
							t.async_statusCode = rs.getInt("async");
							t.verbs = rs.getString("http_method");
							t.name = String.valueOf(rs.getInt("ID")); 										
							t.conn.setRequestProperty("X-forwarded-host", location );
							t.conn.setRequestProperty("Accept-method",rs.getString("http_method").replaceFirst("(\\sOPTIONS|OPTIONS\\s)", ""));
							subrequests.add(t);
							t.start();
						} catch (Exception e) {
							continue;
						}
					}	
					sql.close();
					
					json.key("services");
					json.object();
						//first join and add all responses to OPTIONS 
						for(SubRequest t: subrequests) {
				        	try {	        		
				        		t.join();
				        		//TODO ssl on|off must be somehow passed     			
			        			try {
			        				String id = t.conn.getRequestProperty("X-forwarded-host") +":"+ t.verbs + ( t.async_statusCode>0 ? ":Notify": ":Request");
			        				json.key(id);
			        				json.object();			        					
			        					json.key("url"); json.value(t.conn.getRequestProperty("X-forwarded-host"));
			        					json.key("verbs"); json.value(t.verbs);
			        					json.key("async"); json.value(t.async_statusCode);
			        					json.key("cast"); json.value(1);
			        					json.key("api");
			        					if (t.error != null) {						        				
						        			json.value(null);
						        			json.key("error");
						        			json.value(t.error.getLocalizedMessage());
					        			} 
			        					json.value(null);
			        				json.endObject();
			        			} catch( JSONException e) {
			        				log.error("Invalid json stream after OPTIONS",e);			        					        			
			        			}
			        		
				        	} catch (InterruptedException e) {	    
				        		log.error(e.getMessage(), e);
				        		writer.close();
				        		return;
				        	}	        	
				        }
						
						//then select all other endpoints 
						
						qry = "SELECT * FROM endpoints WHERE (uri_base IS NULL OR (uri_base!='/manage' AND uri_base!='/manage/')) AND NOT http_method LIKE '%OPTIONS%' ORDER BY service_endpoint";
						rs = sql.executeQuery(qry);	
						
						ArrayList<Route> routes = new ArrayList<Route>();
						while (rs.next()) {
							String url = ( "http"+(rs.getBoolean("ssl") ? "s" : "") +"://"+ (Utils.blank(rs.getString("gateway_host")) ? context.getHost() : rs.getString("gateway_host")));								
							if (!Utils.blank(rs.getString("uri_base"))) url += rs.getString("uri_base");
							String id = url +":"+ rs.getString("http_method") + ( rs.getString("async")!=null ? ":Notify": ":Request");
							Route R = null;
							for(Route E:routes) {
								if (E.ID.equals(id)) {
									R = E;
									R.cast++;
									break;
								}
							}
							if (R == null) { 
								routes.add(
									new Route(
											id,
											url,
											String.valueOf(rs.getInt("async")),
											rs.getString("http_method")
									)
								);
							}
							
						}
						for(Route E:routes) {
							json.key(E.ID);								
							json.object();
								json.key("url"); json.value(E.url);
								json.key("async"); json.value(E.async);
								json.key("verbs"); json.value(E.method);
								json.key("cast"); json.value(E.cast);		
							json.endObject();
						}					

					json.endObject();
					//////////////////////////////////////////////////////////////////////////////////////////////////
					
				json.endObject();			
			} catch(JSONException e) {
				log.error(e.getMessage(), e);
			} finally {
				subrequests.clear();
				sql.close();
			}		
			writer.close();
			String jsondata = writer.toString();
			
			//process json;
			String path = request.getRequestURI();
			
			if (path.equals("/manage/")) {		
				doManager(jsondata);
				
			} else {				
				String filename = System.getProperty("user.dir")+"/.."+path;
				log(filename);
				serveFile(filename);
			}

		} catch(Exception e){
			log.error(e.getMessage(), e);
		}
		*/
	}

	private void doManager(String json) throws IOException,JSONException {
				
		String user = context.getUsername();	
		String session = context.getSessionToken();
		String reply = "<html>" +
		"<head>" +
			"<link rel=\"stylesheet\" type=\"text/css\" href=\"//media.gridport.co/aos/grid.css\" />" +
			"" +
			"<script language=\"javascript\">" +
				"var spanel = null;" +
				"function loaded() {" +
					"l = window.location.toString().split(\"#\");" +
					"if (l.length==\"2\") {	" +
						"spid = \"spanel_ctrl\"+l[1].replace(/\\:/g,\"_\").replace(/\\//g,\"_\");" +
						"document.getElementById(spid).onclick();" +
					"} else" +
						"document.getElementById(\"spanel_ctrl\"+"+"???"+").onclick();" +
				"}" +
			"</script>" +
		"</head>" +
		"<body>" +
		"<div id=\"header\">" +
		"GridPort @ <b>"+context.getHost()+"</b>" +
		" - connection: <b>"+user+"</b> "+context.getConsumerAddr() + " " +  (session!=null ? " <a href='/manage/?logout="+session+"'>[logout]</a>"  : "") +
		"</div><table id=\"gridinfo\">";
		
		JSONObject obj = new JSONObject(json);
		JSONArray a = obj.optJSONArray("processes"); 
		JSONObject s = obj.getJSONObject("services"); 
		
		//process list
		reply +="<tr><td style='border-top:1px solid silver;' colspan='2'><table id='processlist'><thead><tr><th colspan='2'></th></tr></thead><tbody>";
		for( int i=0; i<a.length(); i++) reply +="<tr><td></td><td>" + (String) a.get(i) + "</td></tr>";
		reply +="</tbody></table><br/></td></tr>";
		
		//workspace
		reply += "<tr>";		
		reply += "<td width='300' style='border-right:1px solid silver;'>"; //left column
			//services
		reply += "<h2>services</h2>";
		Iterator<?> i = s.keys();
		while( i.hasNext()) {
			String service  = (String) i.next();
			JSONObject o = s.getJSONObject(service);
			
			String cssClass = "webapi";
			if (o.getString("verbs").matches(".*OPTIONS.*")) cssClass = "service";
			if (o.getInt("async")>0) cssClass = "event";
			if (o.getInt("cast")>1) cssClass += " multicast";
			reply +="<li class='"+cssClass+"'>";
			reply +="<strong><small>"+o.getString("url")+"</small></strong><ul>";
			reply +=o.getString("verbs");
			reply +="</li>";
			//reply<?> j = o.getJSONObject("").keys();while( j.hasNext()) { log((String) j.next()); }
			reply +="</ul>";
		}
		
		//users
		reply += "<h2>users</h2><table id='userlist'><thead><tr><th>username</th><th>groups</th></tr></thead><tbody>";
		//.,,,
		reply += "</tbody></table>";		
		reply += "</td>"; //end of left column
	
		reply +="<td>"; // right column
		//...
		reply +="</td>"; // end of right column
		//end of workspace
		reply += "</tr>"; 
		
		//end of response
		reply +="</table></body></html>";
		response.setHeader("Content-Type","text/html");
		response.setHeader("Content-Length",String.valueOf(reply.length()));	
		response.setStatus(200);
		response.setContentLength(reply.length());
		response.getWriter().write(reply);
		response.getWriter().close();
	}
}

