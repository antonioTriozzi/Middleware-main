package it.univaq.testMiddleware.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/") // Rotta base
public class TestController {

    @GetMapping
    public String home() {
        return "🏠 Benvenuto nell'API del Middleware!";
    }

    @GetMapping("/test")
    public String test() {
        return "🚀 Questa è una route di test! protetta";
    }
}
