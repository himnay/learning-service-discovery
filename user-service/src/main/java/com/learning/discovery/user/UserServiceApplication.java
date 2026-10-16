package com.learning.discovery.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

@SpringBootApplication
class UserServiceApplication {
    static void main(String[] args) {
        SpringApplication app = new SpringApplication(UserServiceApplication.class);
        app.setApplicationStartup(new BufferingApplicationStartup(2048)); // backs /actuator/startup
        app.run(args);
    }
}
