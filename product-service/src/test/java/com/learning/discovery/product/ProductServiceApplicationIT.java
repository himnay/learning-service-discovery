package com.learning.discovery.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProductServiceApplicationIT {

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void apiWithoutCredentialsReturns401() {
        restTestClient.get().uri("/api/products").exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void fullStackReturnsProductsOverHttp() {
        restTestClient.get().uri("/api/products").headers(h -> h.setBasicAuth("admin", "admin"))
                .accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.length()").isEqualTo(3);
    }

    @Test
    void healthEndpointIsPublicAndReportsUp() {
        restTestClient.get().uri("/actuator/health").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void secureActuatorEndpointsRequireCredentials() {
        restTestClient.get().uri("/actuator/quartz").exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        restTestClient.get().uri("/actuator/quartz").headers(h -> h.setBasicAuth("admin", "admin")).exchange()
                .expectStatus().isOk();
    }
}
