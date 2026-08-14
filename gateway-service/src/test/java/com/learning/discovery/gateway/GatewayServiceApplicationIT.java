package com.learning.discovery.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayServiceApplicationIT {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpointReportsUp() {
        webTestClient.get().uri("/actuator/health").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
