package co.gridport.server.space;

public class SubsGarbage extends Subscription {
	private long interval = 0;
	public SubsGarbage(String regexpattern, long interval_sec) throws SpaceError {
		super(regexpattern, "GarbageCollector");		
		start();		
		interval = interval_sec * 1000;
		Space2.log.info("Garbage collection for "+regexpattern+" runs every " + interval_sec + " seconds" );
	}	
		
	public void execute() throws SpaceError {
		try {
			sleep(interval);
			Space2.CollectGarbage(primary_automaton);
		} catch(InterruptedException e ) {
			this.interrupt();
		}
			
	}
}
