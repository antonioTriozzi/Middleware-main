package it.univaq.testMiddleware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; 
@SpringBootApplication
@EnableScheduling 
public class TestMiddlewareApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestMiddlewareApplication.class, args);
    }
}
