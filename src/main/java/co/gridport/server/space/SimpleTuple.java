package co.gridport.server.space;

import dk.brics.automaton.Automaton;

public class SimpleTuple {	
	protected String descriptor;
	protected String transaction_data; 
	
	public SimpleTuple(String ADescriptor) {
		descriptor = ADescriptor;	
	}
	public SimpleTuple(String ADescriptor,String AData) {
		descriptor = ADescriptor;	
		Write(AData);
	}
	public String getDescriptor() {
		return descriptor;
	}	
	public boolean Match(Automaton A) {
		return A.run(descriptor);
	}	
	public void Write(String ATransactionData ) {
		transaction_data = ATransactionData;
	}	
	public String getData() throws SpaceError {
		return transaction_data;
	}
}