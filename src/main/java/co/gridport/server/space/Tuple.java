package co.gridport.server.space;

public class Tuple extends SimpleTuple {
	public int id = 0;
	private String default_data = null; // if localSegment.media type is MemoryMedia
	public Long exclusiveThread = null; // for in / out transactional access
	public boolean readable; // for in / out transactional access
	public boolean written = true; // for local commit and rollback purpose
	public Long modified;
	
	private boolean garbage = false;
	
	public Tuple(String ADescriptor) { // Constructor represents non-transactional write
		super(ADescriptor);	
		readable = true;
		written = false;				
	}

	public void Write(String ATransactionData) {
		exclusiveThread = Thread.currentThread().getId();
		super.Write(ATransactionData);
		readable = false;
		written = true;
	}
	
	public String retreiveTransactionData() {
		//transaction_data is retreived by media and cleaned up
		String result = transaction_data;
		transaction_data = null;
		return result;
	}
	public String getData() throws SpaceError {
		if (id == 0) return super.getData();
		else return Space2.storage.read(this);
	}
	
	public void bin() {
		Space2.bin++;
		garbage = true;
	}
	public boolean isGarbage() {
		return garbage;
	}

	public String Take() throws SpaceError {
		exclusiveThread = Thread.currentThread().getId();
		readable = true;
		return getData();
	}
	
	public void Rollback() throws SpaceError {
		if (exclusiveThread == Thread.currentThread().getId()) {
			exclusiveThread=null;
			if (written) { // ROLLBACK WRITE
				bin();
			} else { // ROLLBACK TAKE							
				exclusiveThread=null; 
			}
			Space2.storage.sync(this);
		}
	}
	
	// here with revise commit synchronized must be removed
	public synchronized boolean Commit() throws SpaceError {
		if (exclusiveThread==null) {
			throw new SpaceError(000,"Thread-unsafe commit of the tuple");
		}
		
		exclusiveThread = null;
		
		if (readable) { // COMMIT TAKE
			bin();
			Space2.storage.sync(this);
			//if (!written) Space2.Log("/// TAKE [" + descriptor+ "]");			
		} else { // COMMIT WRITE			
			written = false;
			readable = true;
			modified = System.currentTimeMillis();
			Space2.storage.sync(this);
			//Space2.Log("/// WRITE [" + descriptor+ "]");
			Space2.notifyObservers(this);
			return true;
		}
		return false;
	}
	
	public synchronized void defaultSync() {
		if (id == 0) {			
			default_data = retreiveTransactionData();
			id = 1;//Space.localSegment.tuples.indexOf(this);
		} if (garbage) {
			default_data = null;
		}
	}
	
	public String defaultRead() {	
		//TODO in transaction, tuple should not be takable for other threds from now 
		return default_data;
	}
	
	
}
