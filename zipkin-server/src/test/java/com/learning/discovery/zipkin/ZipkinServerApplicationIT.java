package com.learning.discovery.zipkin;

import com.google.protobuf.ByteString;
import com.linecorp.armeria.server.Server;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Boots the module the same way main() does (no @SpringBootTest: Zipkin's bootstrap disables
// auto-configuration and uses its own config name) on a random port, without Eureka.
class ZipkinServerApplicationIT {

    private static ConfigurableApplicationContext context;
    private static String baseUrl;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void start() {
        context = ZipkinServerApplication.run("--QUERY_PORT=0", "--DISCOVERY_EUREKA_ENABLED=false");
        baseUrl = "http://localhost:" + context.getBean(Server.class).activeLocalPort();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void healthReportsOtlpCollectorUp() throws Exception {
        HttpResponse<String> health = get("/health");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("OpenTelemetryHttpCollector");
    }

    @Test
    void otlpSpanIsQueryableInZipkinFormat() throws Exception {
        long now = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder()
                        .setResource(Resource.newBuilder().addAttributes(KeyValue.newBuilder()
                                .setKey("service.name")
                                .setValue(AnyValue.newBuilder().setStringValue("it-service"))))
                        .addScopeSpans(ScopeSpans.newBuilder().addSpans(Span.newBuilder()
                                .setTraceId(ByteString.copyFrom(HexFormat.of().parseHex("0af7651916cd43dd8448eb211c80319c")))
                                .setSpanId(ByteString.copyFrom(HexFormat.of().parseHex("b7ad6b7169203331")))
                                .setName("get /api/it")
                                .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                                .setStartTimeUnixNano(now)
                                .setEndTimeUnixNano(now + TimeUnit.MILLISECONDS.toNanos(5)))))
                .build();

        HttpResponse<String> accepted = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/traces"))
                        .header("Content-Type", "application/x-protobuf")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(request.toByteArray()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(202);

        // the collector stores asynchronously
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(get("/api/v2/trace/0af7651916cd43dd8448eb211c80319c").body())
                        .contains("\"name\":\"get /api/it\"")
                        .contains("\"serviceName\":\"it-service\""));
    }

    @Test
    void uiIsServed() throws Exception {
        assertThat(get("/zipkin/").statusCode()).isEqualTo(200);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
