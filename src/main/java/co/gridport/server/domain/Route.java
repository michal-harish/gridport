package co.gridport.server.domain;

import java.util.ArrayList;

import co.gridport.server.SubRequest;

public class Route {
	public boolean wildcard;
	public String gateway;
	public String method;
	public String gateway_host;
	public String async;
	//public String[] auth_group;	
	public String uri;	
	public String endpoint;
	public String query_stirng;
	public String base_uri;
	public String context;
	
	public Integer ID;
	public int cast = 1;
	public String url;
	
	public ArrayList<Contract> contracts = new ArrayList<Contract>();
	public boolean defaultRoute = false;
	
	public SubRequest subrequest;
	
	public Route(
			Integer ID,
			String aurl,
			String aasync,
			String averbs
	) {
		this.ID = ID;
		url = aurl;
		cast = 1;
		async = aasync;
		method = averbs; 
	}
	
	public Route(
			boolean awildcard,
			Integer ID,
			String agateway,
			String amethod,
			String agateway_host,
			String aasync,
			//String aauth_group,	
			String auri,
			String aendpoint,
			String aquery_stirng,
			String abase_uri,
			String acontext
			) {		
		wildcard = awildcard;
		this.ID = ID;
		gateway = agateway;
		method = amethod;
		gateway_host = agateway_host;
		async = aasync;
		/*
		ArrayList<String> groups = new ArrayList<String> ();
		for(String g:(aauth_group == null ? "default" : aauth_group).split("[\\s\\n\\r,]")) {
			if (!Server.none(g.trim())) groups.add(g.trim());
		}
		auth_group = groups.toArray(new String[groups.size()]);
		*/
		
		uri = auri.replaceAll("\\/{2,}","\\/");	
		endpoint = aendpoint;
		query_stirng = aquery_stirng;
		base_uri = abase_uri;
		context = acontext;
	}
}
