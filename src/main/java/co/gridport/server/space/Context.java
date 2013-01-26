package co.gridport.server.space;
/**
 * Context attached to each thread which is accessing the space
 * therfore all its attributes are thread-safe 
 */

import java.util.ArrayList;

public class Context {
	//public boolean behindBarrier = false; 
	public Long threadId = null;
	public boolean transaction = false;
	public ArrayList<Tuple> modifications = new ArrayList<Tuple>(); // Tuples modified within transaction
	public Context(Long aThreadId) {
		threadId = aThreadId;
	}
	public boolean modified() {
		return modifications.size()>0;
	}	
	protected void modification(Tuple T) {
		if (transaction && !modifications.contains(T)) {				
			//Space.Log("Modification "+T.getDescriptor());
			modifications.add(T);		
		}
	}	
	public void reset() {		
		modifications.clear();
		transaction = false;
	}
	protected ArrayList<Observer> observers = new ArrayList<Observer>();
	protected void addObserver(Observer o) {
		observers.add( o );
	}	
	protected void removeObserver(Observer o) {
		observers.remove(o);		
	}	
	
	
}
