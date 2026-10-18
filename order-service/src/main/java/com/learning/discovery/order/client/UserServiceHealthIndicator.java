package com.learning.discovery.order.client;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * "userService" in /actuator/health (SBA Details → Health): how many user-service instances
 * Eureka currently knows. Reports UNKNOWN, not DOWN, when there are none — /with-user degrades
 * to "user": null, so a missing dependency must not mark order-service itself DOWN (and it is
 * kept out of the readiness group for the same reason).
 */
@Component
@RequiredArgsConstructor
class UserServiceHealthIndicator implements HealthIndicator {

    private static final String USER_SERVICE = "user-service";

    private final DiscoveryClient discoveryClient;

    @Override
    public Health health() {
        List<ServiceInstance> instances = discoveryClient.getInstances(USER_SERVICE);
        Health.Builder builder = instances.isEmpty() ? Health.unknown() : Health.up();
        return builder.withDetail("instances", instances.size())
                .withDetail("urls", instances.stream().map(i -> i.getUri().toString()).toList())
                .build();
    }
}
