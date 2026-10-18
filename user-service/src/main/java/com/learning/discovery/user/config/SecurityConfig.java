package com.learning.discovery.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SecurityProperties security) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(security.publicPaths().toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // stateless API clients and SBA (POST /actuator/env, /refresh, ...) send no CSRF token
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/actuator/**"))
                .build();
    }
}
