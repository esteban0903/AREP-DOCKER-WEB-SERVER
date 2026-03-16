package com.eci.arep.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class HelloControllerTest {

    private final HelloController controller = new HelloController();


    @Test
    void shouldReturnPiMessage() {
        assertEquals("PI= " + Math.PI, controller.getPI());
    }

    @Test
    void shouldReturnHelloMessage() {
        assertEquals("hello world", controller.hello());
    }
}
