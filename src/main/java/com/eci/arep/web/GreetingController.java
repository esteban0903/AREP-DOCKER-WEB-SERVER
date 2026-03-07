package com.eci.arep.web;

import java.util.concurrent.atomic.AtomicLong;

@RestController
public class GreetingController {

    private static final String TEMPLATE = "Hola %s";
    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/greeting")
    public String greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
        long call = counter.incrementAndGet();
        return String.format(TEMPLATE, name) + " (request #" + call + ")";
    }
}