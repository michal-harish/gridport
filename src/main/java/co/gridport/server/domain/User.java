package co.gridport.server.domain;

import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import co.gridport.server.utils.Crypt;
import co.gridport.server.utils.Utils;

public class User {

    private String username;
    private List<String> groups;
    private String passport;

    public User(String username, List<String> groups, String passport) {
        this.username = username;
        this.groups = groups;
        this.passport = passport;
    }

    public String getUsername() {
        return username;
    }

    public List<String> getGroups() {
        return groups;
    }

    @JsonProperty("hasPassword")
    public Boolean hasPassword() {
        return !Utils.blank(passport);
    }

    @JsonIgnore
    public String getPassport(String realm) {
       if (Utils.blank(realm)) {
           return Utils.blank(passport) ?  Crypt.md5(username +"::") : passport;
       } else throw new NotImplementedException();
    }

    public void createPassport(String realm, String password) {
        if (Utils.blank(realm)) {
            passport = Crypt.md5(username +":" + realm + ":" + password);
        } else throw new NotImplementedException();
    }

}
