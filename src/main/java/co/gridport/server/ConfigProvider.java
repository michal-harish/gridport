package co.gridport.server;

import java.util.Collection;
import java.util.Map;

import co.gridport.server.domain.Contract;
import co.gridport.server.domain.Endpoint;
import co.gridport.server.domain.User;

public interface ConfigProvider {

    Map<String, String> getSettings();
    Boolean has(String string);
    String get(String string);
    String put(String string, String string2);

    Collection<Contract> getContracts();
    Contract getContract(String contractName);
    Contract updateContract(Contract contract);

    Map<String,Endpoint> getEndpoints();

    Collection<User> getUsers();
    User getUser(String username);
    User updateUser(User user);

    void close();



}
