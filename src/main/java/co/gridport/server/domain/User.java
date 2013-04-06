package co.gridport.server.domain;

public class User {

    private String username;
    private String groups;

    public User(String username, String groups) {
        this.username = username;
        this.groups = groups;
    }

    public String getUsername() {
        return username;
    }

    public String getGroups() {
        return groups;
    }

}
