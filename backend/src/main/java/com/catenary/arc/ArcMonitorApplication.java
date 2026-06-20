package com.catenary.arc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArcMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArcMonitorApplication.class, args);
    }
}
