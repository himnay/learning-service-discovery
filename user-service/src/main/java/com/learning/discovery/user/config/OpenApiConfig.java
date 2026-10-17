package com.learning.discovery.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Spec at /v3/api-docs, Swagger UI at /swagger-ui.html, both public (SecurityConfig). The API itself needs
// HTTP Basic, so the spec declares that scheme globally: Swagger UI's "Authorize" button then sends the
// credentials on "Try it out". The relative server URL makes "Try it out" call whichever origin served
// the UI: this service directly, or gateway-service's aggregated Swagger UI (http://localhost:8080).
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

    @Bean
    OpenAPI openApi(@Value("${spring.application.name}") String name,
                    @Value("${info.app.description}") String description) {
        return new OpenAPI()
                .info(new Info().title(name).description(description).version("v1"))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
