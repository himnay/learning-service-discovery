package com.learning.discovery.eureka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Dashboard and registry API behind HTTP Basic (clients use http://admin:admin@host:8761/eureka/);
 * health/info stay public for probes. CSRF is off for /eureka/** because Eureka clients
 * POST/PUT/DELETE registrations without a token, and for /actuator/** so SBA can invoke endpoints.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info", "/error").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .formLogin(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**", "/actuator/**"))
                .build();
    }
}
