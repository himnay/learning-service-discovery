package com.learning.discovery.user.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;

// Keeps Zipkin readable: Spring Boot Admin polls /actuator/** every few seconds and the Eureka client
// heartbeats every 30 s, so without this each poll/heartbeat would be a trace of its own. Spring
// Security's per-filter observations are dropped too, leaving one server span per request.
@Configuration(proxyBeanMethods = false)
class TracingConfig {

    @Bean
    ObservationPredicate skipActuatorEurekaAndSecurityObservations() {
        return (name, context) -> {
            if (name.startsWith("spring.security.")) {
                return false;
            }
            if (context instanceof ServerRequestObservationContext server) {
                return !server.getCarrier().getRequestURI().startsWith("/actuator");
            }
            if (context instanceof ClientRequestObservationContext client && client.getCarrier() != null) {
                return !client.getCarrier().getURI().getPath().startsWith("/eureka");
            }
            return true;
        };
    }
}
