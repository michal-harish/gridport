package co.gridport.server.space;


public abstract class Medium {
	
	abstract public String info();
	abstract public void sync(Tuple T) throws SpaceError;
	abstract public String read(Tuple T) throws SpaceError;
	abstract public void cleanup();
	
	protected void Log( String message ) {
		Space2.log.error( message );
	}
	protected void initializeTuple(int aID, String aDescriptor, boolean aWritten, long aModified) {
		Space2.initializeTuple(aID, aDescriptor, aWritten,aModified);
	}
}



