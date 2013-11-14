package co.gridport.server;

import javax.ws.rs.Path;

import org.reflections.Reflections;

public class Utils {

    public static String scanRestEasyResources(String packageName) {
        String resteasyResources = "";
        Reflections reflections = new Reflections(packageName);
        for(Class<?> type: reflections.getTypesAnnotatedWith(Path.class)) {
            resteasyResources += (resteasyResources.equals("") ? "" : ",") + type.getName();
            System.out.println("Adding resource " + type.getName());
        }
        return resteasyResources;
    }

}
