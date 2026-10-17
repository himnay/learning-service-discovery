package com.learning.discovery.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Public so @WebMvcTest slices can @Import it. The single user comes from spring.security.user.*.
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // Browser login creates a JDBC-backed session, visible in /actuator/sessions
                .formLogin(Customizer.withDefaults())
                // Stateless API clients and SBA's actuator POSTs carry no CSRF token
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/actuator/**"))
                .build();
    }
}
