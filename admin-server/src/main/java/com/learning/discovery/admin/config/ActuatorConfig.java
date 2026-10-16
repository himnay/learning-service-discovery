package com.learning.discovery.admin.config;

import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.actuate.audit.InMemoryAuditEventRepository;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ActuatorConfig {

    @Bean   // backs /actuator/auditevents (SBA UI login success/failure, see SecurityConfig)
    AuditEventRepository auditEventRepository() {
        return new InMemoryAuditEventRepository();
    }

    @Bean   // backs /actuator/httpexchanges (recorded by HttpExchangesWebFilter on WebFlux)
    HttpExchangeRepository httpExchangeRepository() {
        return new InMemoryHttpExchangeRepository();
    }
}
