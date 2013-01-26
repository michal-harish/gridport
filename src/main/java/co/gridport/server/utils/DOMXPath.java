package co.gridport.server.utils;

import java.io.*;
import java.util.List;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;

import javax.xml.xpath.XPathFactory;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;
import javax.xml.xpath.XPathExpressionException;

public class DOMXPath { 
	public Document doc;
	private XPath xpath;
	private Transformer aTransformer;
	public static DOMXPath newInstance( File F ) throws IOException {
		try {
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		    domFactory.setNamespaceAware(true); // never forget this!
		    DocumentBuilder builder = domFactory.newDocumentBuilder();	
		    Document doc = builder.parse( F );
		    return new DOMXPath(doc);
		} catch (ParserConfigurationException e) {
			throw new IOException("Configuration exception "+ e.toString());
		} catch (SAXException e) {
			throw new IOException("SAX Exception "+ e.toString());
		}			
	}
	public static DOMXPath newInstance( String S ) throws IOException {
		try {
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		    domFactory.setNamespaceAware(true); // never forget this!
		    DocumentBuilder builder = domFactory.newDocumentBuilder();		    
		    Document doc = builder. parse( new InputSource(new StringReader(S)) );
		    return new DOMXPath(doc);
		} catch (ParserConfigurationException e) {
			throw new IOException("Configuration exception "+ e.toString());
		} catch (SAXException e) {
			throw new IOException("SAX Exception "+ e.toString());
		}			
	}
	
	
	public DOMXPath(Document document ) throws IOException {
	    doc = document;
	    XPathFactory factory = XPathFactory.newInstance();	    
	    xpath = factory.newXPath();		
	    try {
	    	TransformerFactory tranFactory = TransformerFactory.newInstance();	    
	    	aTransformer = tranFactory.newTransformer();
	    } catch(TransformerConfigurationException e) {
			throw new IOException("Configuration exception "+ e.toString());
		} 
	}	
	public List<Element> query(String querystring) throws XPathExpressionException {
		return query(querystring,doc.getFirstChild());		
	}	
	public List<Element> query(String querystring, Node n) throws XPathExpressionException {	
		NodeList L = (NodeList) xpath.evaluate( querystring, n, XPathConstants.NODESET);
		List<Element> a = new ArrayList<Element>();
		for (int i=0; i<L.getLength(); i++) 
			if (L.item(i) instanceof Element) 
				a.add((Element) L.item(i));
		return a;
	}	
	
	public Element query1(String querystring) throws XPathExpressionException {
		return query1(querystring, doc.getFirstChild());
	}
	public Element query1(String querystring, Node n) throws XPathExpressionException {		
		NodeList nodes = (NodeList) xpath.evaluate( querystring, n,XPathConstants.NODESET);		
		if (nodes.getLength()!=1) throw new XPathExpressionException("Query1 must result into single element "+querystring);
		return (Element) nodes.item(0);
	}
	
	public Element create(String name, Element parent) {	
		Element result = doc.createElement(name);
		parent.appendChild( result );
		return result;
	}
	
	public String toXML() {	

		StringWriter dest = new StringWriter();
		try {
			aTransformer.transform(new DOMSource(doc), new StreamResult(dest));
			return dest.toString();
		} catch(TransformerException e) {
			return null;
		}
		 	
		
	}	
	
}
