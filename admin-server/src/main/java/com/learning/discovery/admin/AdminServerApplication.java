package com.learning.discovery.admin;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

@EnableAdminServer
@SpringBootApplication
class AdminServerApplication {
    static void main(String[] args) {
        SpringApplication app = new SpringApplication(AdminServerApplication.class);
        app.setApplicationStartup(new BufferingApplicationStartup(2048));   // backs /actuator/startup
        app.run(args);
    }
}
