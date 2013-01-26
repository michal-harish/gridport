package co.gridport.server.space;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
 
public class MediumSQLite extends Medium {
	
	Connection conn;		
	
	
	private String escape(String data) {
		if (data == null) return "";
		else return data.replace("'","''");
	}
	
	private String info = "";
	public String info() {
		return info;
	}
	
	public MediumSQLite() throws Exception {		
		Class.forName("org.sqlite.JDBC");		
		conn = DriverManager.getConnection("jdbc:sqlite:space.db");
		Statement stat = conn.createStatement();
		stat.execute("CREATE TABLE IF NOT EXISTS tuples (" +
				"id INTEGER PRIMARY KEY AUTOINCREMENT" +
				",txn"+
				",garbage BOOL"+
				",written BOOL"+
				",modified " +
				",descriptor" +				
				",data BLOB" +
				")");		
		ResultSet rs = stat.executeQuery("SELECT name FROM SQLITE_MASTER WHERE name='tuples'");
        if (!rs.next()) throw new Exception("Default dataset does not exist.");
        rs.close();  
        cleanup();
        rs = stat.executeQuery("SELECT id,descriptor,written,modified FROM tuples");
        int initialized = 0;
        while (rs.next()) {
        	initializeTuple(
        			rs.getInt("id")
        			,rs.getString("descriptor") 
        			,rs.getBoolean("written")
        			,rs.getLong("modified")
        	);
        	//info = "T "+rs.getString("descriptor");
        	initialized++;
        }
        rs.close();
        info += initialized +" Tuple(s)"; 
        
        stat.close();
	}

	public void finalize() throws SQLException {
		conn.close();
		info = "Closing SQLite Media";
	}	
	
	public void cleanup() {		
		try {
			Statement stat = conn.createStatement();
			//clean up garbage
	        stat.execute("DELETE FROM tuples WHERE txn IS NULL AND garbage = 1");
	        //revert broken TAKEs
	        stat.execute("UPDATE tuples SET garbage = 0, txn = null WHERE txn IS NOT NULL AND garbage = 1");
	        //revert broken WRITEs
	        stat.execute("DELETE FROM tuples WHERE written = 1 AND txn IS NOT NULL") ;
	        stat.close();
		} catch (SQLException e) {
			Space2.log.warn("MediumSQLite Cleanup Error "+e.getMessage());
		}
	}

	public void sync(Tuple T) throws SpaceError {
		try {	
			Statement stat = conn.createStatement();	
			if (T.id == 0) { // CREATE OR REQUEST WRITE	
				
				ResultSet rs;
				int insert_id;
				String qry="INSERT INTO tuples (garbage,written,modified,descriptor,data) VALUES("+
				"0"+
				","+(T.written?"1":"0")+			
				","+String.valueOf(T.modified)+
				",'"+escape(T.getDescriptor())+"'"+
				",'"+escape(T.retreiveTransactionData())+"'"+
				")";
				//Log("+SQLite "+qry);
				synchronized(this) {
					stat.executeUpdate(qry);			
					stat.close();
					Statement stat2 = conn.createStatement();
					rs = stat2.executeQuery("SELECT last_insert_rowid()");
					insert_id = rs.getInt(1);
					rs.close();
					stat2.close();
				}				
				//Log("+SQLite ["+insert_id+"]");
				T.id=insert_id;				

			} else if (T.isGarbage()) {	// REMOVE TAKEN OR ROLLBACK WRITE
				synchronized(this) {
					stat.execute("UPDATE tuples SET garbage=1 WHERE id="+T.id);
					stat.close();
				}
				//Log("-SQLite "+T.id);
				
			} else { // 2PC REQUEST TAKE OR CONFIRM WRITE OR ROLLBACK TAKE
				synchronized(this) {
					stat.executeUpdate("UPDATE tuples SET " +									
							"written=0"+
							" WHERE id="+T.id);
					stat.close();
				}
				Log("=SQLite "+T.id);
			}
		} catch (SQLException e) {
			throw new SpaceError(11,e.toString());
		}
	}
	
	public String read(Tuple T) throws SpaceError {
		try {		
			String result;
			synchronized(this) {
					Statement stat = conn.createStatement();	
					ResultSet rs = stat.executeQuery("SELECT data FROM tuples WHERE id="+T.id);
					result = rs.getString(1);
					rs.close();
					stat.close();
			}
			return result;
		} catch (SQLException e) {
			Space2.log.error(e.getMessage(), e);
			throw new SpaceError(11,e.toString());
		}
	}
	

}
