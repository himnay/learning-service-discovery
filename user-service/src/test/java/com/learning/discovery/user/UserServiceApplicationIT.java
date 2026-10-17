package com.learning.discovery.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserServiceApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;
    private RestTestClient authedClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        authedClient = restTestClient.mutate().defaultHeaders(h -> h.setBasicAuth("admin", "admin")).build();
    }

    @Test
    void fullStackReturnsFlywaySeededUsersOverHttp() {
        authedClient.get().uri("/api/users").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.length()").isEqualTo(5);
    }

    @Test
    void unknownUserReturns404() {
        authedClient.get().uri("/api/users/999").exchange().expectStatus().isNotFound();
    }

    @Test
    void readinessIncludesDb() {
        restTestClient.get().uri("/actuator/health/readiness").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.components.db.status").isEqualTo("UP");
    }

    @Test
    void healthEndpointReportsUp() {
        restTestClient.get().uri("/actuator/health").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void sbaEndpointsAreExposed() {
        authedClient.get().uri("/actuator").exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$._links.sbom").exists()
                .jsonPath("$._links.jolokia").exists()
                .jsonPath("$._links.httpexchanges").exists()
                .jsonPath("$._links.auditevents").exists();
    }

    @Test
    void apiWithoutCredentialsReturns401() {
        restTestClient.get().uri("/api/users").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void sensitiveActuatorEndpointRequiresCredentials() {
        restTestClient.get().uri("/actuator/quartz").exchange().expectStatus().isUnauthorized();
        authedClient.get().uri("/actuator/quartz/jobs/demo/heartbeatJob").exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.name").isEqualTo("heartbeatJob");
    }

    @Test
    void openApiSpecIsPublicAndDeclaresBasicAuth() {
        restTestClient.get().uri("/v3/api-docs").exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.components.securitySchemes.basicAuth.scheme").isEqualTo("basic")
                .jsonPath("$.servers[0].url").isEqualTo("/")
                .jsonPath("$.paths['/api/users/{id}'].get.responses['404']").exists();
    }

    @Test
    void swaggerUiIsPublic() {
        // /swagger-ui.html redirects to /swagger-ui/index.html; the client follows it, so both must be public
        restTestClient.get().uri("/swagger-ui.html").exchange().expectStatus().is2xxSuccessful();
    }
}
