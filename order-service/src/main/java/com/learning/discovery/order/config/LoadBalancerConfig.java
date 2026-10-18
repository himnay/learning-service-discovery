package com.learning.discovery.order.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
class LoadBalancerConfig {

    // @Primary and unqualified so Eureka's own DiscoveryClient (which resolves its HTTP
    // client via an unqualified RestClient.Builder lookup) gets this one instead of the
    // @LoadBalanced bean below — otherwise Eureka's own registration calls get routed
    // through the load balancer and can never succeed.
    @Bean
    @Primary
    RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(boundedTimeouts());
    }

    // RestClient.builder() is not Boot's auto-configured builder, so it has no ObservationRegistry by
    // default. Setting it makes each call to user-service a client span that sends the traceparent
    // header, so gateway -> order-service -> user-service shows up as one trace in Zipkin.
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder()
                .requestFactory(boundedTimeouts())
                .observationRegistry(observationRegistry);
    }

    /**
     * Bounded connect/read timeouts — the JDK client's default read timeout is infinite, so a hung upstream would pin the calling thread.
     */
    private static JdkClientHttpRequestFactory boundedTimeouts() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }
}
