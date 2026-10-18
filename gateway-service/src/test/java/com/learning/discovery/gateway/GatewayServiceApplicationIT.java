package com.learning.discovery.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = RANDOM_PORT)
class GatewayServiceApplicationIT {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpointReportsUpWithoutCredentials() {
        webTestClient.get().uri("/actuator/health").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void protectedPathWithoutCredentialsIsUnauthorized() {
        webTestClient.get().uri("/actuator/env").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedPathWithCredentialsIsAllowedAndAudited() {
        webTestClient.get().uri("/actuator/env")
                .headers(h -> h.setBasicAuth("admin", "admin"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/actuator/auditevents?type=AUTHENTICATION_SUCCESS")
                .headers(h -> h.setBasicAuth("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.events[0].principal").isEqualTo("admin");
    }

    @Test
    void quartzHeartbeatJobIsRegistered() {
        webTestClient.get().uri("/actuator/quartz/jobs/demo/heartbeatJob")
                .headers(h -> h.setBasicAuth("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("heartbeatJob");
    }

    @Test
    void aggregatedSwaggerUiListsTheServiceSpecs() {
        webTestClient.get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
        webTestClient.get().uri("/v3/api-docs/swagger-config").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls.length()").isEqualTo(3)
                .jsonPath("$.urls[?(@.name == 'order-service')].url").isEqualTo("/api-docs/order-service");
    }
}
