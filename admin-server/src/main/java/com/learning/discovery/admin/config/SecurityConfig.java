package com.learning.discovery.admin.config;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * SBA's documented "secure the server" setup, translated to WebFlux: the SBA 4 server starter runs on
 * Netty/WebFlux, so this is a {@link SecurityWebFilterChain} rather than a servlet SecurityFilterChain.
 * <ul>
 *   <li>Browser requests without a session are redirected to SBA's own {@code /login} page (form login);
 *       non-HTML requests get a 401 Basic challenge.</li>
 *   <li>CSRF token lives in an {@code XSRF-TOKEN} cookie readable by the SBA Vue app (axios sends it back as
 *       {@code X-XSRF-TOKEN}); {@code /instances/**} (client self-registration) and {@code /actuator/**} are exempt.</li>
 *   <li>No rememberMe: reactive Spring Security doesn't offer it (checkbox hidden via spring.boot.admin.ui.remember-me-enabled).</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

    private final AdminServerProperties adminServer;

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                  SecurityProperties security,
                                                  ReactiveUserDetailsService userDetailsService,
                                                  ApplicationEventPublisher eventPublisher) {
        ReactiveAuthenticationManager authenticationManager =
                auditing(new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService), eventPublisher);

        var logoutSuccess = new RedirectServerLogoutSuccessHandler();
        logoutSuccess.setLogoutSuccessUrl(URI.create(adminServer.path("/login?logout")));

        return http
                .authorizeExchange(auth -> auth
                        .pathMatchers(adminServer.path("/assets/**"), adminServer.path("/variables.css"),
                                adminServer.path("/login")).permitAll()
                        .pathMatchers(security.publicPaths().toArray(String[]::new)).permitAll()
                        .anyExchange().authenticated())
                .formLogin(form -> form
                        .loginPage(adminServer.path("/login"))
                        .authenticationManager(authenticationManager)
                        .authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler(adminServer.path("/"))))
                .logout(logout -> logout
                        .logoutUrl(adminServer.path("/logout"))
                        .logoutSuccessHandler(logoutSuccess))
                .httpBasic(basic -> basic.authenticationManager(authenticationManager))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(new AndServerWebExchangeMatcher(
                                CsrfWebFilter.DEFAULT_CSRF_MATCHER,
                                new NegatedServerWebExchangeMatcher(csrfIgnored()))))
                .build();
    }

    private ServerWebExchangeMatcher csrfIgnored() {
        return ServerWebExchangeMatchers.pathMatchers(
                adminServer.path("/instances"), adminServer.path("/instances/**"), "/actuator/**");
    }

    /**
     * The CSRF token is deferred on WebFlux; subscribe to it on every request so the XSRF-TOKEN cookie is
     * always written for the SBA single-page app. Runs after the security chain (lowest precedence).
     */
    @Bean
    WebFilter csrfCookieWebFilter() {
        return (exchange, chain) -> exchange
                .<Mono<CsrfToken>>getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty())
                .then(chain.filter(exchange));
    }

    /**
     * Reactive security publishes no authentication events by default; wrap the manager so Boot's
     * AuthenticationAuditListener turns SBA UI / Basic logins into AUTHENTICATION_SUCCESS / _FAILURE audit events.
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
