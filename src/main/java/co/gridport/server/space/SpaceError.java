package co.gridport.server.space;

public class SpaceError extends Exception {
	
	static final long serialVersionUID = 1;
	private int code = 0;
	
	public SpaceError(int errorcode, String errormessage) {
		super(errormessage);
		code = errorcode;
		
	}
	
	public int getCode() {
		return code;
	}
	
}
