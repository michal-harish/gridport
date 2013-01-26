package co.gridport.server.space;

import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

public class Space2 {
	static protected Logger log = LoggerFactory.getLogger("mod_space");
	static boolean initialized = false;	
	static protected Medium storage;	
	static public void initialize(Class<? extends Medium> MediumClass) throws SpaceError {
		synchronized(Space2.class) {
			if (initialized) return;
			try {			
				storage = (Medium) MediumClass.newInstance();
				log.info("Init storage " + storage.info());				
				initialized = true;
				SimpleTuple[] subscriptions = Space2.READ("notification\\(.*?\\)");
				for(SimpleTuple T:subscriptions ) {
					String pattern = T.getData();
					String target = T.getDescriptor().substring(13,T.getDescriptor().length()-1);
					log.info("Init subscription "+pattern+ " TO "+target);
					new SubsNotify(pattern,target);
				}
				new SubsGarbage(".*",60);
			} catch (IllegalAccessException e) {
				throw new SpaceError(000,"IllegalAccessException when initializing storage");
			} catch (InstantiationException e) {
				throw new SpaceError(000,"InstantiationException of storage");
			}
		}
	}

	
	static protected HashMap<Long,Context> contexts = new HashMap<Long,Context>(); 
	static protected Context threadContext() {
		synchronized(contexts) {
			long tid = Thread.currentThread().getId();
			if (contexts.containsKey(tid)) {
				return contexts.get(tid);
			} else {
				//Log("NEW CONTEXT FOR THREAD "+tid);
				Context C = new Context(tid);
				contexts.put(tid,C);
				return C;
			}				
		}
	}
	
	static protected int bin = 0;
	static protected ArrayList<Tuple> tuples = new ArrayList<Tuple>();
	
	static protected void initializeTuple(int aID, String aDescriptor, /*String aDlock,*/ boolean aWritten, long aModified) {
		Tuple T = new Tuple(aDescriptor);
		T.id = aID;		
		//if (aDlock!=null && !aDlock.isEmpty()) T.dlock = aDlock;
		T.written = aWritten;
		T.readable = (!aWritten);
		T.modified = aModified;
		tuples.add(T);
		//if (T.dlock!=null) recovery.add(T);
	}	
	
	static public void BEGIN() {
		threadContext().transaction = true;		
	}
	/*
	public long COUNT(String pattern) throws InterruptedException {
		Automaton A = new RegExp(pattern).toAutomaton();
		return COUNT(A);
	}
	public long COUNT(Automaton A) throws InterruptedException {
		crossBarrier();					
		try {								
			long result = 0;	
			int atomicNow=tuples.size();
			for(int i=atomicNow-1; i>=0; i--) {
				Tuple T=tuples.get(i);
				synchronized(T) {
					if (!T.isGarbage() && T.Match( A )) result++;
				}
			}				
			return result;
		} finally {
			exitBarrier();
		}			
	}	
	*/
	static public void WRITE(String descriptor, String data ) throws SpaceError {
		Context C = threadContext();		
		Tuple T = new Tuple(descriptor);
		T.Write(data);
		synchronized(tuples) {
			synchronized(T) {
				tuples.add(T);
			}
		}		
		if (!C.transaction) {
			//Log("N"+T.id);
			T.Commit();
		} else {
			C.modification(T);
		}
	}

	//take(pattern) pattern synchronizes on single tuple 
	static public SimpleTuple TAKE(String pattern, long timeout_ms ) throws InterruptedException,SpaceError {		
		ObserverTake o =  ObserverTake.Create(pattern);
		return o.Next(timeout_ms);					
	}
	
	static public SimpleTuple READ(String pattern, long timeout_ms ) throws InterruptedException,SpaceError {		
		ObserverRead o =  ObserverRead.Create(pattern);
		return o.Next(timeout_ms);					
	}		
	
	// loose read
	static public SimpleTuple[] READ(String pattern)  throws SpaceError {
		Context C= threadContext();
		synchronized(tuples) {	
			Automaton A = new RegExp(pattern).toAutomaton();
			ArrayList<SimpleTuple> result = new ArrayList<SimpleTuple>();
			int sizeNow=tuples.size();
			for(int i=0; i<sizeNow; i++) {
				Tuple T=tuples.get(i);
				synchronized(T) { // synchronized added along with ReviseCommit disablement
					if (!T.isGarbage() && T.Match( A ))	{							
						if (C.transaction) {
							//written by another thread is not visible inside transaction
							if (T.written && T.exclusiveThread!=C.threadId) continue;
							//already taken by this thread is not visible inside transaction
							if (T.readable && T.exclusiveThread==C.threadId) continue;
						} else if (!T.readable || T.isGarbage()) continue;
						result.add( new SimpleTuple(T.getDescriptor(),T.getData() ));
										
					}
				}
			}			
			return result.toArray(new SimpleTuple[result.size()]);
		}	
	}	

	static public void COMMIT() throws SpaceError {
		//TODO three phase commit, String unique_txn = ...
		//1.update each tuple in the storage flags(written,garbage) with their future value + txn = unique_txn 
		//2.issue an atomic operation to set all txn = 'READY' WHERE txn = unique_txn
		//3.loop again through each tuple ; storage.sync(T) and notifyObservers(T);
		//this way semi commited transaction will not happen because 
		//	- if the code does not pass step 2 everything will be reverted on next startup and no-one will be notified
		//  - if the code breaks during loop 3, then it will be finished off on the next startup initializeTuple()
		for(Tuple T:threadContext().modifications) {
			if (T == null) log.warn("! NULL(T) Transaction Complete ");
			else {								
				synchronized( T ) {
					T.Commit();
				}				
			}
		}		
		
		threadContext().reset(); // reset localsegment modification status
		
		synchronized(contexts) { // WARNING this used to be a localSegment object !
			contexts.notifyAll(); // for all concurent threads to chase again
		}
	}	
	
	static public void ROLLBACK() throws SpaceError {			
		if (threadContext().modifications.size()>0) {
			log.warn("*** ROLLBACK ***");
			for(Tuple T:threadContext().modifications) {			
				if (T == null) log.warn("! NULL(T) Transaction");			
				else {					
					synchronized(T) {					
						T.Rollback();		
					}
				}
			}
		}
	}

	static protected int CollectGarbage(Automaton A) {
		int binned = 0;
		synchronized(Space2.tuples) {
			for(int i=Space2.tuples.size()-1; i>=0; i--) {
				Tuple T = Space2.tuples.get(i);
				if (T.isGarbage() && T.Match(A)) {							
					Space2.tuples.remove(i);
					try {
						storage.sync(T);
					} catch(SpaceError e) {
						log.warn("Problem with removing garbage tuple "+T.getDescriptor()+" "+e.getMessage(),e);
					}
					binned++;
					bin--;
				}
			}	
			if (binned>0) {
				storage.cleanup();
			}
		}			
		return binned;
	}	
	
	//////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////
	
	static public ArrayList<Subscription> subs = new ArrayList<Subscription>();

	/*
	static String ListObservers(int olderThanMS) {
		String result="";
			for(Observer T:observers) {			
				result += T.getClass().getSimpleName().substring(0,3).toUpperCase()+"[ "+T.pattern+" ]\r\n";
			}
		return result;
	}	
	*/	


	static protected void notifyObservers(Tuple T) throws SpaceError {
		synchronized(contexts) {	
			if (!T.isGarbage()) {	
				for(Long key:contexts.keySet() ) { 
					//Read Observers must be notified first so that all are satisfied before the tuple is taken
					for(Observer O:contexts.get(key).observers ) if (O instanceof ObserverRead) {						
						if (!O.tupleWritten(T))
							log.info("NOTIFY READER "+T.getDescriptor()+" UNSUCCESFUL "+O.pattern);
						else log.info("NOTIFY READER "+T.getDescriptor()+" NOT SUCCESFUL "+O.pattern);
					}
					//Take Observers simply compete TODO rotation order for take observers
					for(Observer O:contexts.get(key).observers ) if (O instanceof ObserverTake) O.tupleWritten(T);
				}
			}
		}
	}

	
	//////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////
	
	/*
	static protected long logMs() { 
		long timestamp = System.currentTimeMillis(); 
		return timestamp;
	}

	static protected void logMs(long ts,String info) {
		long res =System.currentTimeMillis() - ts;
		Log(String.valueOf(res)+" "+info);
	}	
	*/
	
	static public void close() {
		for(Subscription S:subs) {
			S.interrupt();
		}
	}	
}
