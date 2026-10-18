# <span style="color:hsl(13,80%,58%)">zipkin-server</span>

Distributed tracing backend for the service-discovery demo: a Spring Boot module, like `eureka-server`,
that runs the Zipkin server through `@EnableZipkinServer`, with an OTLP/HTTP receiver so the services
can export straight to it. No Docker. See the root [../README.md](../README.md) for the whole picture.

## <span style="color:hsl(151,80%,58%)">How spans get here</span>

```mermaid
flowchart LR
    GW["gateway-service"] -- " OTLP/HTTP " --> ZIP
    ORD["order-service"] -- " OTLP/HTTP " --> ZIP
    USR["user-service"] -- " OTLP/HTTP " --> ZIP
    PRD["product-service"] -- " OTLP/HTTP " --> ZIP
    ZIP["zipkin-server :9411<br/>/v1/traces (OTLP) · /api/v2/** · UI /zipkin<br/>in-memory storage"] -- " register " --> EUR["eureka-server :8761"]
```

- ***Services speak OpenTelemetry*** — `gateway-service`, `user-service`, `order-service` and `product-service`
  have `spring-boot-starter-opentelemetry` (Micrometer Tracing + OTel bridge + OTLP exporter) and export to
  `management.opentelemetry.tracing.export.otlp.endpoint` = `http://localhost:9411/v1/traces`
  (override with `OTLP_TRACES_ENDPOINT`).
- ***Zipkin takes OTLP itself*** — [`zipkin-module-otel`](https://github.com/openzipkin-contrib/zipkin-otel)
  adds the `/v1/traces` receiver on the same port as the UI and API, and converts OTLP spans to Zipkin's model.
  No OpenTelemetry Collector in between.
- ***Propagation*** — W3C `traceparent`. The gateway's routed calls and order-service's load-balanced
  `RestClient` (`LoadBalancerConfig` sets the `ObservationRegistry`) send it, so
  `GET /api/orders/101/with-user` through the gateway is one trace across three services.
- ***Sampling 100 %*** — `management.tracing.sampling.probability: 1.0` (Boot's default is 0.1).
- ***Noise filtered*** — each service's `TracingConfig` drops `/actuator/**` (Spring Boot Admin polling),
  `/eureka/**` client calls (heartbeats) and Spring Security's per-filter spans.
- ***Log correlation*** — Boot adds `[traceId-spanId]` to the services' log lines; grep a trace id from Zipkin in
  `logs/*.log`.
- ***Tests*** — the services set `management.tracing.export.enabled: false` in `application-test.yml`.

## <span style="color:hsl(288,80%,58%)">Why this module has no parent and runs Boot 3.5</span>

| Fact                                                                                                                                                                                                      | Consequence                                                                                                                                                      |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@EnableZipkinServer` from `io.zipkin.java:zipkin-server` was last released as 2.12.9 (2019, Boot 2.1)                                                                                                    | Not usable on Boot 4 / Java 27                                                                                                                                   |
| Current `io.zipkin:zipkin-server` 3.6.1 is built on ***Spring Boot 3.5.12*** + Armeria; it still has `zipkin2.server.internal.EnableZipkinServer` (internal package: Zipkin supports only its own server) | This module uses that annotation and copies `zipkin.server.ZipkinServer`'s bootstrap                                                                             |
| On Boot 4.1 it fails at startup: `ClassNotFoundException: org.springframework.boot.web.server.Compression` (moved by Boot 4's modularization)                                                             | No `<parent>`: the repo's chain (super-pom → Boot 4.1) and `learning-bom` (imports `spring-boot-dependencies` 4.1) would override Zipkin's Boot 3.5 dependencies |
| No Zipkin server BOM/starter exists (only client BOMs: `brave-bom`, `zipkin-reporter-bom`)                                                                                                                | [pom.xml](pom.xml) lists 3 artifacts: `zipkin-server`, `zipkin-lens` (UI), `zipkin-module-otel`; the rest is transitive                                          |

Compiled for ***Java 27*** (`maven.compiler.release`), runs on the same JDK as the other modules. Still listed
in the root `<modules>`, so `mvn verify` builds and tests it.

## <span style="color:hsl(66,80%,50%)">Configuration</span>

Zipkin disables Boot auto-configuration and reads `zipkin-server*.yml` (`spring.config.name=zipkin-server`):

| File                                                                        | From                                                        | Content                                                                                  |
|-----------------------------------------------------------------------------|-------------------------------------------------------------|------------------------------------------------------------------------------------------|
| [zipkin-server.yml](src/main/resources/zipkin-server.yml)                   | this module, ***shadows*** the one in the zipkin-server jar | `spring.profiles.include: shared, otel, learning` — later profiles override earlier ones |
| `zipkin-server-shared.yml`                                                  | zipkin-server jar                                           | every setting and its env var (`QUERY_PORT`, `STORAGE_TYPE`, `EUREKA_SERVICE_URL`, ...)  |
| `zipkin-server-otel.yml`                                                    | zipkin-module-otel jar                                      | registers the OTLP/HTTP collector                                                        |
| [zipkin-server-learning.yml](src/main/resources/zipkin-server-learning.yml) | this module                                                 | Eureka: `http://admin:admin@localhost:8761/eureka`, app name `zipkin-server`             |

## <span style="color:hsl(256,80%,58%)">Run</span>

```bash
mvn -pl zipkin-server spring-boot:run      # or: java -jar zipkin-server/target/zipkin-server-0.0.1-SNAPSHOT.jar
```

- UI: http://localhost:9411 (redirects to `/zipkin/`), health: http://localhost:9411/health
- Registers in Eureka as `ZIPKIN-SERVER` if `eureka-server` is up; otherwise logs a warning and keeps running.
- Start order does not matter: services started before Zipkin only drop the spans they could not export.

```bash
curl -u admin:admin localhost:8080/api/orders/101/with-user
```

Expected trace (***Run Query*** in the UI): `gateway-service` server + client span → `order-service`
`http get /api/orders/{id}/with-user` → `order-service` client span → `user-service` `http get /api/users/{id}`.

## <span style="color:hsl(171,80%,58%)">Tests</span>

[ZipkinServerApplicationIT](src/test/java/com/learning/discovery/zipkin/ZipkinServerApplicationIT.java) boots
the server the way `main()` does (`QUERY_PORT=0`, Eureka off), posts an OTLP protobuf span to `/v1/traces`
and reads it back from `/api/v2/trace/{id}` in Zipkin format; also checks `/health` and the UI.

## <span style="color:hsl(220,80%,62%)">Gotchas</span>

| Symptom                                                         | Cause                                                                                    | Fix                                                                                                     |
|-----------------------------------------------------------------|------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `Failed to export spans ... Connection refused` in service logs | zipkin-server not running                                                                | Start it; the services keep working without it                                                          |
| Zipkin empty right after a request                              | Boot batches spans (5 s schedule delay)                                                  | Wait a few seconds, re-run the query                                                                    |
| Order and user spans in separate traces                         | A `RestClient`/`WebClient` built without an `ObservationRegistry` sends no `traceparent` | Use Boot's builder, or set `.observationRegistry(..)` like `order-service`                              |
| `Failed to register ... to Eureka`                              | eureka-server down                                                                       | Harmless; set `DISCOVERY_EUREKA_ENABLED=false` to stop trying                                           |
| Traces gone after restart                                       | in-memory storage                                                                        | Expected for the demo; `STORAGE_TYPE=elasticsearch`/`cassandra3`/`mysql` need the optional storage jars |
