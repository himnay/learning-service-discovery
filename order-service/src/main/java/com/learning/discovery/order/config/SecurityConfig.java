package com.learning.discovery.order.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Public so @WebMvcTest slices can @Import it. The single user comes from spring.security.user.*.
@Configuration
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
                // Browser login creates a JDBC-backed session, visible in /actuator/sessions
                .formLogin(Customizer.withDefaults())
                // Stateless API clients and SBA's actuator POSTs carry no CSRF token
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/actuator/**"))
                .build();
    }
}
