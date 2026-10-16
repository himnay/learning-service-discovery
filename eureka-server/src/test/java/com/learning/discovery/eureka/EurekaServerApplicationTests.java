package com.learning.discovery.eureka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

// no self-registration in tests: the default defaultZone points at :8761, not the random test port
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.register-with-eureka=false")
class EurekaServerApplicationTests {

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void contextLoads() {
        // wiring smoke test: EnableEurekaServer must bootstrap without errors
    }

    @Test
    void dashboardIsServedAtRootWithCredentials() {
        restTestClient.get().uri("/").headers(h -> h.setBasicAuth("admin", "admin")).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(String.class).value(body -> assertThat(body).contains("Eureka"));
    }

    @Test
    void healthEndpointIsPublicAndReportsUp() {
        restTestClient.get().uri("/actuator/health").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void registryApiRejectsAnonymousCallers() {
        restTestClient.get().uri("/eureka/apps").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void registryApiAcceptsBasicAuth() {
        restTestClient.get().uri("/eureka/apps").accept(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBasicAuth("admin", "admin")).exchange()
                .expectStatus().isOk();
    }

    @Test
    void sbaEndpointsAreExposedBehindAuth() {
        restTestClient.get().uri("/actuator/quartz").exchange().expectStatus().isUnauthorized();
        // "startup" is absent here: it needs BufferingApplicationStartup, which only main() installs
        for (String endpoint : new String[] {"quartz", "httpexchanges", "auditevents", "jolokia", "sbom"}) {
            restTestClient.get().uri("/actuator/" + endpoint).headers(h -> h.setBasicAuth("admin", "admin")).exchange()
                    .expectStatus().isOk();
        }
    }
}
