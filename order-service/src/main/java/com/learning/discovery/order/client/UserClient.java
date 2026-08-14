package com.learning.discovery.order.client;

import com.learning.discovery.order.model.UserSummary;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

// Calls user-service directly (bypassing gateway-service) to show service-to-service
// load-balanced calls: "http://USER-SERVICE" is a Eureka service id, resolved to a live
// instance by Spring Cloud LoadBalancer because the RestClient.Builder is @LoadBalanced.
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl("http://USER-SERVICE").build();
    }

    public Optional<UserSummary> fetchUser(Long userId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/users/{id}", userId)
                    .retrieve()
                    .body(UserSummary.class));
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }
}
