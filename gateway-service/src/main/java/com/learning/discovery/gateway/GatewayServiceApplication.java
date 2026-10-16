package com.learning.discovery.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

@SpringBootApplication
class GatewayServiceApplication {
    static void main(String[] args) {
        SpringApplication app = new SpringApplication(GatewayServiceApplication.class);
        app.setApplicationStartup(new BufferingApplicationStartup(2048));   // backs /actuator/startup (SBA Startup view)
        app.run(args);
    }
}
