package co.gridport.server.domain;

import java.util.ArrayList;
import java.util.List;

import joptsimple.internal.Strings;

import org.apache.commons.lang.NotImplementedException;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import co.gridport.server.Crypt;

public class User {

    private String username;
    private List<String> groups;
    private String passport;

    public User(String username, String groups, String passport) {
        this.username = username;
        setGroups(groups);
        this.passport = passport;
    }

    public String getUsername() {
        return username;
    }

    public List<String> getGroups() {
        return groups;
    }
    public void setGroups(String groups) {
        this.groups = new ArrayList<String>();
        for(String group:groups.split("[\\s\\,\\;]")) {
            if (!Strings.isNullOrEmpty(group)) this.groups.add(group);
        }
    }

    @JsonProperty("hasPassword")
    public Boolean hasPassword() {
        return !Strings.isNullOrEmpty(passport);
    }

    @JsonIgnore
    public String getPassport(String realm) {
       if (Strings.isNullOrEmpty(realm)) {
           return Strings.isNullOrEmpty(passport) ?  Crypt.md5(username +"::") : passport;
       } else throw new NotImplementedException();
    }

    public void setPassword(String realm, String password) {
        if (Strings.isNullOrEmpty(realm)) {
            passport = Crypt.md5(username +":" + realm + ":" + password);
        } else throw new NotImplementedException();
    }

}
