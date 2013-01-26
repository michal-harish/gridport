package co.gridport.server.space;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

public class ObserverRead extends Observer {
	public ObserverRead(Automaton A, String ptrn) throws SpaceError {
		super(A,ptrn);
	}
	static public ObserverRead Create(String ptrn) throws SpaceError {
		return new ObserverRead( new RegExp( ptrn ).toAutomaton(),ptrn );
	}	
	protected SimpleTuple GetTuple(Tuple T) throws SpaceError {
		synchronized(T) {
			Prospects.remove(T);
			return T;
		}
	}
}