package com.learning.discovery.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserServiceApplicationIT {

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
    void fullStackReturnsUsersOverHttp() {
        authedClient.get().uri("/api/users").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.length()").isEqualTo(3);
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
}
