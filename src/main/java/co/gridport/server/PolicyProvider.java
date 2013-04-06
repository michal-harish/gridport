package co.gridport.server;

import java.util.List;
import java.util.Map;

import co.gridport.server.domain.Contract;
import co.gridport.server.domain.Endpoint;
import co.gridport.server.domain.User;

public interface PolicyProvider {

    Map<String, String> getSettings();
    Boolean has(String string);
    String get(String string);
    String put(String string, String string2);

    List<Contract> getContracts();

    List<Endpoint> getEndpoints();

    List<User> getUsers();

    void close();


}
