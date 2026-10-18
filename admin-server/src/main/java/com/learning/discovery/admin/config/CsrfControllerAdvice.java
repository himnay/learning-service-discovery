package com.learning.discovery.admin.config;

import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * SBA's Thymeleaf templates (login.html, sba-settings.js) read {@code ${_csrf}}. Servlet security exposes it as a
 * request attribute automatically; on WebFlux it has to be put into the model, otherwise the login form posts
 * without a token and gets 403.
 */
@ControllerAdvice
class CsrfControllerAdvice {

    @ModelAttribute("_csrf")
    Mono<CsrfToken> csrfToken(ServerWebExchange exchange) {
        return exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty());
    }
}
