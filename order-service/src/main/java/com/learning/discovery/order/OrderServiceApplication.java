package com.learning.discovery.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

@SpringBootApplication
class OrderServiceApplication {
    static void main(String[] args) {
        SpringApplication app = new SpringApplication(OrderServiceApplication.class);
        // Records startup steps for /actuator/startup (SBA "Startup" view)
        app.setApplicationStartup(new BufferingApplicationStartup(2048));
        app.run(args);
    }
}
