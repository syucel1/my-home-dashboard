package dev.homehub.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point: assembles domain, core, persistence, web and drivers into one Spring Boot application. */
@SpringBootApplication(scanBasePackages = "dev.homehub")
public class HomeHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomeHubApplication.class, args);
    }
}
