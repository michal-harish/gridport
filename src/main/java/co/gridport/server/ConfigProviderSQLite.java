package co.gridport.server;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.gridport.server.domain.Contract;
import co.gridport.server.domain.Endpoint;
import co.gridport.server.domain.User;
import co.gridport.server.utils.Utils;

public class ConfigProviderSQLite implements ConfigProvider {

    private static Logger log = LoggerFactory.getLogger("server");

    private Connection policydb;

    private Map<String, String> settings;

    private Map<String,User> users;

    private List<Contract> contracts;

    private Map<String,Endpoint> endpoints;

    @Override
    public Map<String, String> getSettings() {
        return Collections.unmodifiableMap(settings);
    }

    @Override
    public Boolean has(String settingsKey) {
        return settings.containsKey(settingsKey);
    }

    @Override
    public String put(String settingsKey, String settingsValue) {
        try {
            Statement s = policydb.createStatement();
            s.addBatch("DELETE FROM settings WHERE name='"+settingsKey+"'");
            s.addBatch("INSERT INTO settings(name,value) VALUES('"+settingsKey+"','"+settingsValue+"')");
            s.executeBatch();
            s.close();
            return settings.put(settingsKey, settingsValue);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String get(String settingsKey) {
        return settings.get(settingsKey);
    }


    @Override
    public Collection<User> getUsers() {
        return Collections.unmodifiableCollection(users.values());
    }

    @Override
    public User getUser(String username) {
        return users.get(username);
    }

    @Override
    public User updateUser(User user) {
        try {
            Statement s = policydb.createStatement();
            s.addBatch("REPLACE INTO users(groups,username,passport) VALUES(" +
                "'"+StringUtils.join(user.getGroups(),",")+"'" +
                ",'"+user.getUsername()+"'" +
                ",'"+user.getPassport("")+"'" +
            ")");
            s.executeBatch();
            s.close();
            return users.put(user.getUsername(),user);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<Contract> getContracts() {
        return Collections.unmodifiableList(contracts);
    }

    @Override
    public Map<String,Endpoint>  getEndpoints() {
        return Collections.unmodifiableMap(endpoints);
    }

    @Override
    public void close() {
        //shut down plicy db
        try {
            log.info("*** Shutting down policy db");
            if (policydb !=null ) policydb.close();
        } catch (SQLException e2) {
            log.error("*** shut_down",e2);
        }
    }

    public ConfigProviderSQLite() throws SQLException,IOException,ClassNotFoundException   {
        String policyDbFile = "./policy.db";
        Class.forName("org.sqlite.JDBC");

        int version = 0;

        File f = new File(policyDbFile);
        if (f.exists()) {
            policydb = DriverManager.getConnection("jdbc:sqlite:" + policyDbFile);
        } else  {
            log.info("initializing " + policyDbFile);
            policydb = DriverManager.getConnection("jdbc:sqlite:" + policyDbFile);
            Statement s = policydb.createStatement();
            s.addBatch("CREATE TABLE endpoints (ssl NUMERIC, service_endpoint TEXT, http_method TEXT, gateway_host TEXT, ID INTEGER PRIMARY KEY, async TEXT, auth_group TEXT, gateway TEXT, uri_base TEXT);");
            s.addBatch("CREATE TABLE settings (name TEXT, value TEXT);");
            s.addBatch("CREATE TABLE users (groups TEXT, username TEXT);");
            s.addBatch("CREATE UNIQUE INDEX config ON settings(name ASC);");
            s.addBatch("CREATE UNIQUE INDEX user ON users(username ASC);");
            s.executeBatch();
            s.close();

            s.addBatch("INSERT INTO settings(name,value) VALUES('httpPort','8040')");
            s.addBatch("INSERT INTO settings(name,value) VALUES('sslPort','')");
            s.addBatch("INSERT INTO settings(name,value) VALUES('keyStoreFile','')");
            s.addBatch("INSERT INTO settings(name,value) VALUES('keyStorePass','')");
            s.addBatch("INSERT INTO settings(name,value) VALUES('generalTimeout','')");
            s.addBatch("INSERT INTO endpoints(ID,http_method,uri_base,service_endpoint) VALUES(1,'GET','/manage/*','module://manager')");
            s.addBatch("INSERT INTO endpoints(ID,http_method,uri_base,service_endpoint) VALUES(2,'GET POST MOVE PUT OPTIONS','/space/*','module://space')");            
            s.addBatch("INSERT INTO endpoints(ID,http_method,uri_base,service_endpoint) VALUES(3,'GET POST','/example/*','http://localhost:80/')");
            s.executeBatch();
            s.close();

            version = 6;
        }

        log.info("*** POLICY-DB CONNECTED: " + f.getAbsolutePath());        

        Statement s = policydb.createStatement();

        ResultSet rs;
        rs = s.executeQuery("SELECT value FROM settings WHERE name='configVersion'"); 
        if (rs.next()) {
            version = rs.getInt("value");
        }
        s.close();

        try {
            if (version <1 ) { s.executeUpdate("ALTER TABLE endpoints ADD async TEXT "); s.close(); version = 1; }
            if (version <2 ) { s.executeUpdate("ALTER TABLE endpoints ADD auth_group TEXT "); s.close(); version = 2; }        
            if (version <3 ) { 
                    s.executeUpdate("CREATE TABLE users (groups TEXT, username TEXT) ");s.close();
                    s.executeUpdate("CREATE UNIQUE INDEX user ON users(username ASC)");s.close();
                    version = 3; 
            }
            if (version <4 ) {s.executeUpdate("ALTER TABLE endpoints ADD gateway TEXT"); s.close(); version = 4;}
            if (version <5 ) {s.executeUpdate("ALTER TABLE endpoints ADD uri_base TEXT"); s.close(); version = 5;}
            if (version <6 ) {
                version = 6;
            }

            if (version <7 ) {
                s.addBatch("CREATE TABLE contracts (name TEXT, content TEXT, ip_range TEXT, interval REAL, frequency INTEGER)");
                s.executeBatch();
                s.close(); 
                version = 7;
            }
            if (version <8 ) {
                s.addBatch("ALTER TABLE contracts ADD auth_group TEXT ");
                s.addBatch("INSERT INTO users(groups,username) VALUES('examplegroup','exampleuser')");
                s.addBatch("INSERT INTO contracts(name,content,ip_range) VALUES('localAdmin','1,2','127.0.0.1,0:0:0:0:0:0:0:1')");
                s.addBatch("INSERT INTO contracts(name,content,interval,frequency,auth_group) VALUES('examplecontract','3',1.0,1,'examplegroup')");
                s.executeBatch();
                s.close();
                version = 8; 
            }
            if (version <9 ) {
                s.addBatch("CREATE TEMPORARY TABLE temp_table (ID INTEGER PRIMARY KEY,gateway_host TEXT, http_method TEXT, uri_base TEXT, ssl NUMERIC, async TEXT, service_endpoint TEXT, gateway TEXT)");
                s.addBatch("INSERT INTO temp_table SELECT ID,gateway_host, http_method, uri_base, ssl, async, service_endpoint, gateway FROM endpoints");
                s.addBatch("DROP TABLE endpoints");
                s.addBatch("CREATE TABLE endpoints (ID INTEGER PRIMARY KEY,gateway_host TEXT, http_method TEXT, uri_base TEXT, ssl NUMERIC, async TEXT, service_endpoint TEXT, gateway TEXT)");
                s.addBatch("INSERT INTO endpoints SELECT * FROM temp_table");
                s.addBatch("DROP TABLE temp_table");
                s.addBatch("DELETE FROM settings WHERE name='R-service-log'");
                s.executeBatch();
                s.close(); 
                version = 9; 
            }
            if (version <10 ) {
                s.addBatch("DELETE FROM settings WHERE name='generalTimeout'");
                s.executeBatch();
                s.close(); 
                version = 10; 
            }
            if (version <11 ) {
                s.addBatch("ALTER TABLE users ADD passport TEXT ");
                s.executeBatch();
                s.close(); 
                version = 11; 
            }

        } finally {
            log.info("*** POLICY-DB Version: "+ version);
            s = policydb.createStatement();
            s.addBatch("DELETE FROM settings WHERE name='configVersion'");
            s.addBatch("INSERT INTO settings(name,value) VALUES('configVersion','"+version+"')");
            s.executeBatch();
            s.close();
        }

        initializeSettings();
        initializeUsers();
        initializeContracts();
        initializeEndpoints();

    }

    private void initializeUsers() throws SQLException {
        users = new HashMap<String,User>();
        String qry = "SELECT * FROM users";
        Statement sql = policydb.createStatement();
        ResultSet rs = sql.executeQuery(qry);
        while (rs.next()) {
            User user = new User(
                rs.getString("username"),
                rs.getString("groups"),
                rs.getString("passport")
            );
            users.put(user.getUsername(),user);
        }
    }

    private void initializeEndpoints() throws SQLException {
        endpoints = new HashMap<String,Endpoint>();
        String qry = "SELECT * FROM endpoints";
        Statement sql = policydb.createStatement();
        ResultSet rs = sql.executeQuery(qry);
        while (rs.next()) {
            Endpoint endpoint = new Endpoint(
                rs.getString("ID"),
                Utils.blank(rs.getString("ssl")) ? null : rs.getString("ssl").equals("1") ? true : false,
                rs.getString("gateway"),
                rs.getString("gateway_host"),
                rs.getString("http_method"),
                rs.getString("uri_base"),
                rs.getString("service_endpoint").replaceFirst("/$",""),
                rs.getString("async")
            );
            endpoints.put(rs.getString("ID"), endpoint);
        }
    }

    private void initializeSettings() throws SQLException {
        settings = new HashMap<String,String>();
        ResultSet rs;
        rs = policydb.createStatement().executeQuery("SELECT * FROM settings");
        while (rs.next()) if (!Utils.blank(rs.getString("name")) && !Utils.blank(rs.getString("value"))) {
            settings.put(rs.getString("name"), rs.getString("value"));
        }
    }

    private void initializeContracts() {
        //initialize contracts
        contracts = new ArrayList<Contract>();
        try {
            Statement sql = policydb.createStatement();
            try {
                ResultSet rs = sql.executeQuery("SELECT * FROM contracts");
                while (rs.next()) {
                    String name = rs.getString("name") == null ? "default" : rs.getString("name");
                    synchronized(contracts) {
                        ArrayList<String> groups = new ArrayList<String>();
                        if (rs.getString("auth_group") != null && !rs.getString("auth_group").trim().equals("")) {
                            for(String s:rs.getString("auth_group").trim().split("[\\s\\n\\r,]+")) {
                                if (!Utils.blank(s.trim())) groups.add(s.trim());
                            }
                        }
                        ArrayList<String> endpoints = new ArrayList<String>();
                        if (rs.getString("content") != null  && !rs.getString("content").trim().equals("")) {
                            for(String s:rs.getString("content").trim().split("[\\s\\n\\r,]+")) {
                                if (!Utils.blank(s.trim())) endpoints.add(s.trim());
                            }
                        }
                        contracts.add(new Contract(
                            name,
                            rs.getString("ip_range"),
                            new Long(Math.round(rs.getFloat("interval") * 1000)),
                            rs.getLong("frequency"),
                            groups,
                            endpoints.toArray( new String[endpoints.size()])
                        ));
                    }
                }
            } finally {
                sql.close();
            }
        } catch (SQLException e) {
            log.error("Contract SQL error",e);
        }

    }

}
