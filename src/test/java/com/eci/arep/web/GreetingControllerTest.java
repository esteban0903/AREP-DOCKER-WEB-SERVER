package com.eci.arep.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreetingControllerTest {

    @Test
    void shouldReturnGreetingWithProvidedNameAndIncrementCounter() {
        GreetingController controller = new GreetingController();

        assertEquals("Hola Esteban (request #1)", controller.greeting("Esteban"));
        assertEquals("Hola Ana (request #2)", controller.greeting("Ana"));
    }
}
