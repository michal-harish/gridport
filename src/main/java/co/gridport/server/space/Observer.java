package co.gridport.server.space;

import java.util.ArrayList;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

public abstract class Observer {
	
	public String pattern;
	public Automaton automaton;
	protected ArrayList<SimpleTuple> Prospects = new ArrayList<SimpleTuple>();
	
	public Observer(Automaton A, String ptrn) throws SpaceError {
		pattern = ptrn;
		//ObserverTask T = ObserverTask.current();		
		automaton = A;
		rebuildPotential();
		Space2.threadContext().addObserver(this);
	}
	
	final public void extend(String ptrn) {
		synchronized(this) {
			automaton = automaton.union( new RegExp( ptrn ).toAutomaton() );
			automaton.reduce();
		}
		rebuildPotential();
	}
	private synchronized void rebuildPotential() {
		Prospects.clear();
		synchronized (Space2.tuples) {
			for(Tuple T:Space2.tuples) {
				tupleWritten(T);
			}
		}
	}

	final public boolean MatchTuple(SimpleTuple T) {	
		if (T.Match(automaton)) return true;
		else return false;
	}	
	
	final public synchronized boolean tupleWritten(SimpleTuple T) {	
		if (T.Match(automaton)) {
			Prospects.add( T );				
			notifyAll();			
			return true;
		} else {
			return false;
		}
	}
	
	final public synchronized SimpleTuple Next(long timeout) throws InterruptedException,SpaceError  {	
		long limit = System.currentTimeMillis() + timeout;	
		while (true) {
			if (Prospects.size()>0) {
				int l = Prospects.size();
				for(int i = l-1; i>=0; i--) {
					Tuple T = (Tuple) Prospects.get(i);
					if ( MatchTuple(Prospects.get(i))) {														
						synchronized(T) {
							if (T.isGarbage()) {
								Prospects.remove(T);
								continue;			
							}	
							SimpleTuple result = GetTuple(T);
							if (result == null) continue;
							else return result;
						}
					}
				}
			}
			if (timeout == 0 ) {				
				wait();
			} else {	
				if (System.currentTimeMillis()>limit) throw new SpaceError(15,"Observer Time Out");
				wait(timeout);				
			}
		}
	}
	abstract protected SimpleTuple GetTuple(Tuple T) throws SpaceError ;
	
	
}
