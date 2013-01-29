package co.gridport.server.handler;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.GridPortServer;
import co.gridport.server.domain.Contract;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;
import co.gridport.server.utils.Crypt;
import co.gridport.server.utils.Utils;


public class Authenticator extends AbstractHandler
{
    static private Logger log = LoggerFactory.getLogger("authenticator");
    private ArrayList<String> sessions = new ArrayList<String>();  
    
    public void handle(String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response) 
        throws IOException, ServletException
    {
		RequestContext context = (RequestContext) request.getAttribute("context");		
		
		try {
			
			//aggregate auth groups for all available routes 
			String auth_require = "default";
			boolean hasDefaultContracts = false; 
			for(Route E:context.getRoutes()) {
				log.debug("availble route "+E.endpoint);
				if (E.contracts.size()==0) {
					log.debug("route without contract: "+E.endpoint);
					response.setStatus(409);
		            baseRequest.setHandled(true);
		            return;
				}
				for(Contract C:E.contracts) {
					if (C.getAuthGroup().length==0) {
						hasDefaultContracts = true;
					} else for(String g:C.getAuthGroup()) {
						if (auth_require.equals("default")) auth_require="";
						if(Utils.blank(g)) log.warn(C.getName());
						auth_require += (auth_require.equals("") ? "" : ",")+g;
					}
				}	
			}			
			
			if (Utils.blank(auth_require)) {
				log.debug("invalid auth group configuration");
				response.setStatus(409);
	            baseRequest.setHandled(true);
	            return;
			} else if (auth_require.equals("default")) {
				log.debug("auth not required");
				context.setGroups("default");				
				return;
			} else {
				log.debug("auth group aggregate: "+auth_require);
			}

			String groups = null;		    
			String URI = request.getRequestURI().toString();
			String nonce = null;
			String realm = "";
			String qop = "auth";
			String algo = "MD5-sess";	
			int session_index = -1;
		 
			//FIXME shared identity does not work between ( login to manager does require another login to /content etc.)
			if (request.getHeader("Authorization") != null) {			
				String[] a = request.getHeader("Authorization").split(",|\\s");
				String cnonce = null;
				String username = null;
				String reply = null;
				String nc = null;
				for(String S:a) {				
					String[] pa = S.split("=");
					if (pa.length  == 2) {
						String name = pa[0].trim();
						pa[1] = pa[1].trim();
						if (pa[1].substring(pa[1].length()-1).equals(",")) pa[1] = pa[1].substring(0,pa[1].length()-1);
						if (pa[1].substring(0,1).equals("\"")) pa[1] = pa[1].substring(1,pa[1].length()-1);
						String value = pa[1];
						//log("= AUTH " + name + " = " + value );
						if (name.equals("username")) username = value;
						if (name.equals("realm") && !realm.equals(value)) { nonce = null; break; }
						if (name.equals("nonce")) { nonce = value; }
						if (name.equals("uri") && !URI.equals(value)) { nonce = null; break;}
						if (name.equals("digest-uri") && !URI.equals(value)) { nonce = null; break;}
						if (name.equals("algorithm") && !algo.equals(value)) { nonce = null; break; }
						if (name.equals("response")) { reply = value; }
						if (name.equals("qop") && !qop.equals(value)) { nonce = null; break; }
						if (name.equals("nc") ) { nc = value;}
						if (name.equals("cnonce") ) { cnonce = value; }
						//TODO AUTH encoding
					}
				}
				if (nonce !=null)  {
					synchronized(sessions) {
						session_index = sessions.indexOf(nonce);
					}
					
					String[] LOGOUT = URI.split("\\?");
					int logout_index = -1;
					String logout_nonce = null;
					if (LOGOUT.length==2 && LOGOUT[1].length()>7 && LOGOUT[1].substring(0,7).equals("logout=")) {
						logout_nonce = LOGOUT[1].substring(7);				
						synchronized(sessions) {
							logout_index = sessions.indexOf(logout_nonce);							
							if (logout_index>=0) {						
								sessions.remove(logout_index);
								if (session_index>logout_index) session_index--;
								else if (session_index == logout_index) session_index = -1;
								nonce=null;
							} else if (session_index<0)	
								nonce=null;
						}
					}				
							
					if (nonce!=null && qop.equals("auth")) {
						String passport = null;
						groups = null;
						try {
							Statement sql = GridPortServer.policydb.createStatement();		
							try {
								String qry ="";
								for(String g:auth_require.split("[\\s\\,\\;]"))
									qry += (qry.equals("") ? "" : " OR ") + "groups LIKE '%"+g+"%'";
								qry = "SELECT * FROM users WHERE username='"+username+"' AND ("+qry+")";
								ResultSet rs = sql.executeQuery(qry);			
								if (rs.next()) {
									groups = rs.getString("groups");
									passport = Crypt.md5(username +":" + realm + ":" + ""); //FIXME create a /manage operation to issue passports, perhaps even use the apache user and group files format 
								} else {
									log.warn("Unauthorized "+username +"@" +auth_require);
									nonce = null;
								}
							} finally {
								sql.close();
							}
						} catch (SQLException e) {
							nonce = null;						
						}	
						if (nonce !=null) {
							String A1 =  passport + ":" + nonce +":"+cnonce;
							String A2 = request.getMethod() + ":" + URI;
							String data = nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + Crypt.md5(A2);
							String response_match = Crypt.md5(Crypt.md5(A1) + ":" + data);
							if (!reply.equals(response_match))
								nonce = null;
							else {
								context.setUsername(username);
								context.setGroups(groups);
								context.setSessionToken(nonce);
								return;
							}
						}
					} 
				}				
				
			} 
			
			//if not an authenticated request then look for default contracts
			if (hasDefaultContracts) {
				log.debug("default contract(s) available - removing non-default routes and using default guest");
				for(Route E:context.getRoutes()) {
					if (!E.defaultRoute) {
						context.getRoutes().remove(E);
						if (context.getRoutes().size()==0) break;
					}
				}
				context.setUsername("guest");
				context.setGroups("default");
				return;
			}
			
			nonce = Crypt.uniqid(); 
			synchronized(sessions) {
				if (session_index<0) 
					sessions.add(nonce);
				else
					sessions.set(session_index, nonce);
			}
			log.info("DIGEST MD5 CHALLENGE: nonce="+nonce);
			response.setHeader(
			    "WWW-Authenticate", 
			    "Digest realm=\""+realm+"\", algorithm="+algo+", qop=\""+qop+"\", nonce=\""+nonce+"\""
		    );	
			response.setStatus(401);
            baseRequest.setHandled(true);
            return;	
		    
		} catch (Exception e) {
			log.error("GridPortAuthenticator",e);
			response.setStatus(500);
            baseRequest.setHandled(true);
            return; 
		}
	}    	
	
}
