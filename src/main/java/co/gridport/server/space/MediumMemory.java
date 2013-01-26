package co.gridport.server.space;


public class MediumMemory extends Medium {
	public void sync(Tuple T) {
		T.defaultSync();
	}
	public String read(Tuple T) {
		return T.defaultRead();
	}
	public String info() {
		return "?";
	}
	public void cleanup() {
		
	}

}
