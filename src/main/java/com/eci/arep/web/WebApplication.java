package com.eci.arep.web;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class WebApplication {

    static Map<String, Method> controllerMethods = new HashMap<>();

    public static void main(String[] args) throws ClassNotFoundException, IllegalAccessException, InvocationTargetException {
        System.out.println("Loading components ...");

        Class c = Class.forName(args[0]);

        if (c.isAnnotationPresent(RestController.class)) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(GetMapping.class)) {
                    GetMapping a = m.getAnnotation(GetMapping.class);
                    String path = a.value();
                    controllerMethods.put(path, m);
                }
            }
        }
        System.out.println("Invoking method for path: " + args[1]);

        Method m = controllerMethods.get(args[1]);

        m.invoke(null);

        System.out.print(m.invoke(null));

    }

}
