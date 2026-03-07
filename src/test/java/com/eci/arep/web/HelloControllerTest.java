package com.eci.arep.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelloControllerTest {

    private final HelloController controller = new HelloController();

    @Test
    void shouldReturnIndexMessage() {
        assertEquals("Greetings from Spring Boot!", controller.index());
    }

    @Test
    void shouldReturnPiMessage() {
        assertEquals("PI= " + Math.PI, controller.getPI());
    }

    @Test
    void shouldReturnHelloMessage() {
        assertEquals("hello world", controller.hello());
    }
}
