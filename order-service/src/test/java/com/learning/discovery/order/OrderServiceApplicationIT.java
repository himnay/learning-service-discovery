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
    void healthShowsCustomIndicatorsAndProbes() {
        restTestClient.get().uri("/actuator/health").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.components.ordersCache.status").isEqualTo("UP")
                .jsonPath("$.components.ordersCache.details.entries").isEqualTo(6)
                .jsonPath("$.components.userService.status").isEqualTo("UNKNOWN")
                .jsonPath("$.groups").value(groups -> assertThat(groups.toString()).contains("liveness", "readiness"));
        anonymousClient().get().uri("/actuator/health/readiness").exchange().expectStatus().is2xxSuccessful();
        anonymousClient().get().uri("/actuator/health/liveness").exchange().expectStatus().is2xxSuccessful();
    }

    @Test
    void infoShowsAppJavaOsAndProcess() {
        anonymousClient().get().uri("/actuator/info").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.app.name").isEqualTo("order-service")
                .jsonPath("$.app.tier").isEqualTo("backend")
                .jsonPath("$.java.version").exists()
                .jsonPath("$.os.name").exists()
                .jsonPath("$.process.pid").exists();
    }

    @Test
    void customMetricsAreRegistered() {
        restTestClient.get().uri("/api/orders").exchange().expectStatus().is2xxSuccessful();
        restTestClient.get().uri("/actuator/metrics/orders.cache.refresh").exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.measurements[?(@.statistic=='COUNT')].value").value(v ->
                        assertThat(v.toString()).doesNotContain("[0.0]"));
        restTestClient.get().uri("/actuator/metrics/orders.db.reads?tag=op:findAll").exchange()
                .expectStatus().is2xxSuccessful();
    }

    @Test
    void healthEndpointReportsUpWithoutCredentials() {
        anonymousClient().get().uri("/actuator/health").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void openApiSpecIsPublicAndDeclaresBasicAuth() {
        anonymousClient().get().uri("/v3/api-docs").exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.components.securitySchemes.basicAuth.scheme").isEqualTo("basic")
                .jsonPath("$.servers[0].url").isEqualTo("/")
                .jsonPath("$.paths['/api/orders/{id}'].get.responses['404']").exists();
    }

    @Test
    void swaggerUiIsPublic() {
        // /swagger-ui.html redirects to /swagger-ui/index.html; the client follows it, so both must be public
        anonymousClient().get().uri("/swagger-ui.html").exchange().expectStatus().is2xxSuccessful();
    }
}
