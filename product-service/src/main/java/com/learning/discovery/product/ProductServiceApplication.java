package com.learning.discovery.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

@SpringBootApplication
class ProductServiceApplication {
    static void main(String[] args) {
        SpringApplication app = new SpringApplication(ProductServiceApplication.class);
        app.setApplicationStartup(new BufferingApplicationStartup(2048));   // feeds /actuator/startup
        app.run(args);
    }
}
