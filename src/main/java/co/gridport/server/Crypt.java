package co.gridport.server;

import java.security.MessageDigest;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Crypt {
 
    private static Logger log = LoggerFactory.getLogger("server");

    private static final char[] hexChars ={'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
    
	public static String hexStringFromBytes(byte[] b) {
	   String hex = "";
	   int msb;
	   int lsb = 0;
	   int i;
	   // MSB maps to idx 0
	   for (i = 0; i < b.length; i++){
	     msb = ((int)b[i] & 0x000000FF) / 16;
	     lsb = ((int)b[i] & 0x000000FF) % 16;
	       hex = hex + hexChars[msb] + hexChars[lsb];
	   }
	   return(hex);
	} 	
	public static String md5(String base) {
		 try {
			 MessageDigest md = MessageDigest.getInstance("MD5");
			 return hexStringFromBytes(md.digest(base.getBytes()));
		 } catch (Exception e) {
			 log.error(e.getMessage(), e);
		     return null;
		 }		
	}

	public static String sha1(String base) {
		 try {
			 MessageDigest md = MessageDigest.getInstance("SHA-1");    
			 return hexStringFromBytes(md.digest(base.getBytes()));
		 } catch (Exception e) {
			 log.error(e.getMessage(), e);
		     return null;
		 }		
	}	
	public static String sha256(String base) {
		 try {
			 MessageDigest md = MessageDigest.getInstance("SHA-256");    
			 return hexStringFromBytes(md.digest(base.getBytes()));
		 } catch (Exception e) {
		     log.error(e.getMessage(), e);
		     return null;
		 }		
	}		
	public static String sha512(String base) {
		 try {
			 MessageDigest md = MessageDigest.getInstance("SHA-512");    
			 return hexStringFromBytes(md.digest(base.getBytes()));
		 } catch (Exception e) {
		     log.error(e.getMessage(), e);
		     return null;
		 }		
	}	
	public static String uniqid() {
		UUID idOne = UUID.randomUUID();			
		return idOne.toString(); 
	}
}
