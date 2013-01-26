package co.gridport.server.space;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

public class ObserverTake extends Observer {
	public ObserverTake(Automaton A, String ptrn) throws SpaceError {
		super(A,ptrn);
	}
	static public ObserverTake Create(String ptrn) throws SpaceError {
		return new ObserverTake( new RegExp( ptrn ).toAutomaton(), ptrn );
	}
	protected SimpleTuple GetTuple(Tuple T) throws SpaceError {
		synchronized(T) {
			Context C = Space2.threadContext();					
			if (T.exclusiveThread != null) {
				//just taken either by this or other thread
				return null;
			} 
			
			C.modification(T);					
			SimpleTuple result = new SimpleTuple(T.getDescriptor());			
			result.Write( T.Take() );	
			
			if (!C.transaction) {
				Space2.log.info("OBSERVER NON-TRANSACTIONAL TAKE("+T.id+" "+T.getDescriptor()+")");
				T.Commit();
			} else 
				Space2.log.info("OBSERVER TRANSACTION TAKE ("+T.id+" "+T.getDescriptor()+")");
			
			Prospects.remove(T);
			return result;							
		}
				
	}
}
