package com.learning.discovery.order.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
class LoadBalancerConfig {

    // @Primary and unqualified so Eureka's own DiscoveryClient (which resolves its HTTP
    // client via an unqualified RestClient.Builder lookup) gets this one instead of the
    // @LoadBalanced bean below — otherwise Eureka's own registration calls get routed
    // through the load balancer and can never succeed.
    @Bean
    @Primary
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
