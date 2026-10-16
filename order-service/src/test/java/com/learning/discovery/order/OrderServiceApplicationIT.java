package com.learning.discovery.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderServiceApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private CacheManager cacheManager;

    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth("admin", "admin"))
                .build();
    }

    private RestTestClient anonymousClient() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void fullStackReturnsFlywaySeededOrdersOverHttp() {
        restTestClient.get().uri("/api/orders").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.length()").isEqualTo(6);
    }

    @Test
    void schedulerWarmsOrdersCacheOnStartup() {
        assertThat(cacheManager.getCache("orders").get(104L)).isNotNull();
    }

    @Test
    void unknownOrderReturns404() {
        restTestClient.get().uri("/api/orders/999").exchange().expectStatus().isNotFound();
    }

    @Test
    void ordersWithoutCredentialsReturns401() {
        anonymousClient().get().uri("/api/orders").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void wrongPasswordReturns401() {
        anonymousClient().get().uri("/api/orders").accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBasicAuth("admin", "wrong")).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void quartzEndpointListsHeartbeatJob() {
        restTestClient.get().uri("/actuator/quartz/jobs").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.groups.demo.jobs[0]").isEqualTo("heartbeatJob");
    }

    @Test
    void actuatorExposesSbaEndpoints() {
        // "startup" is absent here: only main() installs BufferingApplicationStartup
        var body = restTestClient.get().uri("/actuator").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody();
        for (String link : new String[] {"httpexchanges", "auditevents", "sbom", "quartz", "jolokia", "sessions"}) {
            body.jsonPath("$._links." + link).exists();
        }
    }

    @Test
    void sessionsEndpointIsBackedByJdbcRepository() {
        restTestClient.get().uri("/actuator/sessions?username=admin").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.sessions").isArray();
    }

    @Test
    void healthEndpointReportsUpWithoutCredentials() {
        anonymousClient().get().uri("/actuator/health").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
