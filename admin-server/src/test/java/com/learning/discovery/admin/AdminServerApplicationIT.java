package com.learning.discovery.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = RANDOM_PORT)
class AdminServerApplicationIT {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpointReportsUpWithoutCredentials() {
        webTestClient.get().uri("/actuator/health").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void uiRootWithoutLoginRedirectsToLoginPage() {
        webTestClient.get().uri("/").accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().isFound()
                .expectHeader().location("/login");
    }

    @Test
    void apiWithoutCredentialsIsUnauthorized() {
        webTestClient.get().uri("/applications").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void loginPageIsPublicAndCarriesCsrfToken() {
        webTestClient.get().uri("/login").accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().isOk()
                .expectCookie().exists("XSRF-TOKEN")
                .expectBody(String.class).value(html -> assertThat(html).contains("\"parameterName\":\"_csrf\""));
    }

    @Test
    void formLoginWithCsrfTokenRedirectsToUiAndIsAudited() {
        ResponseCookie xsrf = webTestClient.get().uri("/login").accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseCookies().getFirst("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        webTestClient.post().uri("/login")
                .cookie("XSRF-TOKEN", xsrf.getValue())
                .body(BodyInserters.fromFormData("username", "admin")
                        .with("password", "admin")
                        .with("_csrf", xsrf.getValue()))
                .exchange()
                .expectStatus().isFound()
                .expectHeader().location("/");

        webTestClient.get().uri("/actuator/auditevents?type=AUTHENTICATION_SUCCESS")
                .headers(h -> h.setBasicAuth("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.events[0].principal").isEqualTo("admin");
    }

    @Test
    void formLoginWithoutCsrfTokenIsForbidden() {
        webTestClient.post().uri("/login")
                .body(BodyInserters.fromFormData("username", "admin").with("password", "admin"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void quartzHeartbeatJobIsRegistered() {
        webTestClient.get().uri("/actuator/quartz/jobs/demo/heartbeatJob")
                .headers(h -> h.setBasicAuth("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("heartbeatJob");
    }
}
