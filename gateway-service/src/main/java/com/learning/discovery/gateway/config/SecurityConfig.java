package com.learning.discovery.gateway.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive security for the gateway. Credentials come from spring.security.user.* (ACTUATOR_USER / ACTUATOR_PASSWORD).
 * <p>
 * Downstream services require the same Basic credentials: Spring Cloud Gateway forwards the client's
 * {@code Authorization} header unchanged (only hop-by-hop headers are removed by RemoveHopByHopHeadersFilter),
 * and no route here adds RemoveRequestHeader=Authorization — so a call to /api/** with Basic auth passes through.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                  ReactiveUserDetailsService userDetailsService,
                                                  ApplicationEventPublisher eventPublisher) {
        return http
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health/**", "/actuator/info",
                                "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .anyExchange().authenticated())
                .httpBasic(basic -> basic.authenticationManager(
                        auditing(new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService), eventPublisher)))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)   // stateless API proxy, no browser session
                .build();
    }

    /**
     * Reactive security publishes no authentication events by default; wrap the manager so Boot's
     * AuthenticationAuditListener turns them into AUTHENTICATION_SUCCESS / _FAILURE audit events.
     */
    private static ReactiveAuthenticationManager auditing(ReactiveAuthenticationManager delegate,
                                                          ApplicationEventPublisher eventPublisher) {
        var publisher = new DefaultAuthenticationEventPublisher(eventPublisher);
        return authentication -> delegate.authenticate(authentication)
                .doOnNext(publisher::publishAuthenticationSuccess)
                .doOnError(AuthenticationException.class,
                        ex -> publisher.publishAuthenticationFailure(ex, authentication));
    }
}
