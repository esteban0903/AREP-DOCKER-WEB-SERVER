package com.eci.arep.web;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AnnotationsTest {

    @Test
    void shouldMarkControllersWithRestControllerAnnotation() {
        assertTrue(HelloController.class.isAnnotationPresent(RestController.class));
        assertTrue(GreetingController.class.isAnnotationPresent(RestController.class));
    }

    @Test
    void shouldExposeGetMappingRoutes() throws NoSuchMethodException {
        Method hello = HelloController.class.getDeclaredMethod("hello");
        Method greeting = GreetingController.class.getDeclaredMethod("greeting", String.class);

        assertTrue(hello.isAnnotationPresent(GetMapping.class));
        assertEquals("/hello", hello.getAnnotation(GetMapping.class).value());

        assertTrue(greeting.isAnnotationPresent(GetMapping.class));
        assertEquals("/greeting", greeting.getAnnotation(GetMapping.class).value());
    }

    @Test
    void shouldExposeRequestParamMetadata() throws NoSuchMethodException {
        Method greeting = GreetingController.class.getDeclaredMethod("greeting", String.class);
        Parameter parameter = greeting.getParameters()[0];

        assertTrue(parameter.isAnnotationPresent(RequestParam.class));
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        assertEquals("name", requestParam.value());
        assertEquals("World", requestParam.defaultValue());
    }
}
