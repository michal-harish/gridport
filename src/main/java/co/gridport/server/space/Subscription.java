package co.gridport.server.space;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

public abstract class Subscription extends Thread{
	public String target;
	public String pattern = "?";
	public long started ;
	protected Automaton primary_automaton;
	protected Observer primary_observer;
	public Subscription(String regexpattern, String aTarget) throws SpaceError {
		super();
		target = aTarget;
		pattern = regexpattern;
		started = System.currentTimeMillis() ;
		primary_automaton = new RegExp( regexpattern ).toAutomaton();							
		synchronized(Space2.subs) {
			Space2.subs.add(this);
		}		
	}
	
	abstract void execute() throws SpaceError;
	
	public void run() {

		if (primary_observer == null) {
			Space2.log.info("SUBSCRIPTION ACTIVATED "+this.getClass());			
		} else {
			Space2.log.info("SUBSCRIPTION ACTIVATED "+pattern+" FOR "+target);
		}	
		while (true) {
			try {
				execute();
				if (this.isInterrupted()) {
					Space2.log.info("SUBSCRIPTION ENDED " + this.target + " FOR " + this.pattern );
					break;
				}
			} catch (SpaceError e) {
				Space2.log.info(e.getMessage());				
				continue;
			}
			
		}
	}
}
