package com.learning.discovery.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProductServiceApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

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
    void fullStackReturnsFlywaySeededProductsOverHttp() {
        restTestClient.get().uri("/api/products").headers(h -> h.setBasicAuth("admin", "admin"))
                .accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.length()").isEqualTo(6);
    }

    @Test
    void productByIdKeepsNumericPriceScale() {
        restTestClient.get().uri("/api/products/201").headers(h -> h.setBasicAuth("admin", "admin"))
                .accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Keyboard")
                .jsonPath("$.price").isEqualTo(49.99);
    }

    @Test
    void unknownProductReturns404() {
        restTestClient.get().uri("/api/products/999").headers(h -> h.setBasicAuth("admin", "admin")).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void readinessIncludesDatabase() {
        restTestClient.get().uri("/actuator/health/readiness").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
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

    @Test
    void openApiSpecIsPublicAndDeclaresBasicAuth() {
        restTestClient.get().uri("/v3/api-docs").exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.components.securitySchemes.basicAuth.scheme").isEqualTo("basic")
                .jsonPath("$.servers[0].url").isEqualTo("/")
                .jsonPath("$.paths['/api/products/{id}'].get.responses['404']").exists();
    }

    @Test
    void swaggerUiIsPublic() {
        // /swagger-ui.html redirects to /swagger-ui/index.html; the client follows it, so both must be public
        restTestClient.get().uri("/swagger-ui.html").exchange().expectStatus().is2xxSuccessful();
    }
}
