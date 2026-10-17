# <span style="color:hsl(201,80%,58%)">gateway-service</span>

## <span style="color:hsl(151,80%,58%)">Table of contents</span>

1. 🧭 [Overview](#overview)
2. 🚀 [Run and credentials](#run-and-credentials)
3. 🔐 [Security](#security)
4. 📊 [Actuator endpoints](#actuator-endpoints)
5. ⏱️ [Quartz job, logging and configuration](#quartz-logging-config)
6. 🧪 [Tests](#tests)
7. ⚠️ [Gotchas](#gotchas)
8. ❓ [Q&A](#qa)

API gateway module of [learning-service-discovery](../README.md). The project-wide
picture (all 6 modules, Eureka registry flow, Spring Boot Admin) lives in the root
README; this file covers only what `gateway-service` itself implements.

<a id="overview"></a>
## <span style="color:hsl(288,80%,58%)">1. 🧭 Overview</span>

- ***Single entry point on port 8080*** — routes `/api/users/**`, `/api/orders/**` and `/api/products/**` to the three backend services without knowing a single host or port.
- ***Reactive stack*** — `spring-cloud-starter-gateway-server-webflux` (Netty, Project Reactor), so security, actuator and tests use the WebFlux variants (`SecurityWebFilterChain`, `WebTestClient`), not the servlet ones.
- ***Itself a Eureka client*** — registers as `GATEWAY-SERVICE` and pulls the registry it needs for `lb://` resolution.
- ***Observed by Spring Boot Admin*** — exposes a broad set of actuator endpoints and publishes its credentials in Eureka metadata so `admin-server` can poll them.

**Stack (as of 2026):**

| Concern | Dependency ([pom.xml](pom.xml)) | Version source |
|---|---|---|
| Gateway | `spring-cloud-starter-gateway-server-webflux` | Spring Cloud 2025.1.2 (via root [pom.xml](../pom.xml) / `super-pom` 1.2.1) |
| Discovery | `spring-cloud-starter-netflix-eureka-client` | Spring Cloud BOM |
| Ops | `spring-boot-starter-actuator` | Spring Boot 4.1 |
| Tracing | `spring-boot-starter-opentelemetry` → OTLP to [../zipkin-server](../zipkin-server/README.md) | Spring Boot 4.1 |
| API docs | `springdoc-openapi-starter-webflux-ui` → aggregated Swagger UI at http://localhost:8080/swagger-ui.html (specs routed from `/api-docs/{service}`) | springdoc 3.1 |
| Auth | `spring-boot-starter-security` | Spring Boot BOM |
| Scheduling demo | `spring-boot-starter-quartz` | Spring Boot BOM |
| Tests | `spring-boot-starter-test`, `spring-boot-webtestclient`, `spring-boot-starter-security-test` | Spring Boot BOM |
| SBOM | `cyclonedx-maven-plugin` (declared in the root [pom.xml](../pom.xml)) | `spring-boot-starter-parent` pluginManagement |

**Routes** (declared in [application.yml](src/main/resources/application.yml) under `spring.cloud.gateway.server.webflux.routes`):

| Route id | Predicate | URI | Filters |
|---|---|---|---|
| `user-service` | `Path=/api/users/**` | `lb://USER-SERVICE` | none (path forwarded unchanged) |
| `order-service` | `Path=/api/orders/**` | `lb://ORDER-SERVICE` | none |
| `product-service` | `Path=/api/products/**` | `lb://PRODUCT-SERVICE` | none |

- ***No `StripPrefix` / `RewritePath`*** — `/api/users/1` arrives downstream as `/api/users/1`, matching the backends' own controller mappings.
- ***No `RemoveRequestHeader=Authorization`*** — deliberate, see [§3](#security).
- ***`lb://` = load-balanced service ID*** — Spring Cloud LoadBalancer's `ReactiveLoadBalancerClientFilter` looks the ID up in the gateway's local Eureka cache (refreshed every 30s by default), picks an instance (round-robin by default), and rewrites the request to that instance's `host:port`. Full walk-through with diagram: [root README §2](../README.md#how-the-gateway-finds-services-via-eureka).
- ***Inspect the live route table*** — `GET /actuator/gateway/routes` (authenticated).

<a id="run-and-credentials"></a>
## <span style="color:hsl(66,80%,50%)">2. 🚀 Run and credentials</span>

- ***Start `eureka-server` first***, then the backends, then the gateway (order: [root README §4](../README.md#run-order)).

```bash
cd learning-service-discovery
mvn -pl gateway-service spring-boot:run
# or with custom credentials
ACTUATOR_USER=ops ACTUATOR_PASSWORD=s3cret mvn -pl gateway-service spring-boot:run
```

- ***One user*** — `spring.security.user.name/password` = `${ACTUATOR_USER:admin}` / `${ACTUATOR_PASSWORD:admin}`, role `ACTUATOR`.
- ***The same values are reused three times*** — to log in to the gateway, embedded in the Eureka `defaultZone` URL, and published as Eureka metadata for SBA. Change the env vars on every module together, because the backends check the same header (see [§3](#security)).

```bash
# Through the gateway (Basic creds checked by the gateway AND by the downstream service)
curl -u admin:admin http://localhost:8080/api/users
curl -u admin:admin http://localhost:8080/api/users/1
curl -u admin:admin http://localhost:8080/api/orders
curl -u admin:admin http://localhost:8080/api/products

# Public, no credentials
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info

# Gateway introspection
curl -u admin:admin http://localhost:8080/actuator/gateway/routes

# 401 — no credentials
curl -i http://localhost:8080/api/users
```

<a id="security"></a>
## <span style="color:hsl(256,80%,58%)">3. 🔐 Security</span>

Implemented in [SecurityConfig.java](src/main/java/com/learning/discovery/gateway/config/SecurityConfig.java) (`@EnableWebFluxSecurity`).

- ***Reactive `SecurityWebFilterChain`*** — the gateway runs on WebFlux, so the servlet `SecurityFilterChain` / `HttpSecurity` don't apply; the bean takes `ServerHttpSecurity`, the auto-configured `ReactiveUserDetailsService` (built from `spring.security.user.*`) and the `ApplicationEventPublisher`.
- ***HTTP Basic only*** — no form login, no sessions to speak of; every request carries `Authorization: Basic ...`.
- ***Permitted without auth*** — `/actuator/health/**`, `/actuator/info`, `/swagger-ui/**`, `/v3/api-docs/**`, `/error`. Everything else (`anyExchange().authenticated()`), including all `/api/**` routes and every other actuator endpoint, needs credentials.
- ***CSRF disabled*** — `ServerHttpSecurity.CsrfSpec::disable`: stateless API proxy with no browser session, and SBA's POST actions (shutdown, restart, refresh, env update) would otherwise be rejected.

**Authorization header forwarded downstream:**

- ***Why it matters*** — `user-service`, `order-service` and `product-service` are each secured with the same Basic user, so the backend must receive the client's credentials.
- ***How it happens*** — Spring Cloud Gateway forwards request headers unchanged; its `RemoveHopByHopHeadersFilter` strips only hop-by-hop headers (`Connection`, `Keep-Alive`, `Transfer-Encoding`, ...) and `Authorization` is not one of them.
- ***What would break it*** — adding `RemoveRequestHeader=Authorization` to a route (a common pattern when the gateway is the only trust boundary) would turn every proxied call into a downstream 401. The comment in `application.yml` above `routes:` records this choice.
- ***Trade-off*** — this is credential pass-through, not token relay; a production setup would typically validate a JWT at the gateway and relay it (or mint a service token) instead.

**Audit-event publishing wrapper:**

- ***Problem*** — reactive Spring Security publishes no `AuthenticationSuccessEvent` / failure events by default, so `/actuator/auditevents` (SBA "Audit Events" view) would stay empty.
- ***Fix*** — `auditing(...)` wraps `UserDetailsRepositoryReactiveAuthenticationManager` and plugs it into `httpBasic(basic -> basic.authenticationManager(...))`:

```java
var publisher = new DefaultAuthenticationEventPublisher(eventPublisher);
return authentication -> delegate.authenticate(authentication)
        .doOnNext(publisher::publishAuthenticationSuccess)
        .doOnError(AuthenticationException.class,
                ex -> publisher.publishAuthenticationFailure(ex, authentication));
```

- ***Result*** — Boot's `AuthenticationAuditListener` converts those events into `AUTHENTICATION_SUCCESS` / `AUTHENTICATION_FAILURE` audit events, stored in the `InMemoryAuditEventRepository` from [ActuatorConfig.java](src/main/java/com/learning/discovery/gateway/config/ActuatorConfig.java).

**Request flow:**

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (curl / Insomnia)
    participant G as gateway-service :8080
    participant E as eureka-server :8761
    participant U as user-service :8081

    Note over G,E: Background: gateway registers and pulls registry every 30s<br/>(Basic creds in defaultZone URL)
    G->>E: GET /eureka/apps (admin:admin)
    E-->>G: registry (USER-SERVICE -> 192.168.x.x:8081, ...)

    C->>G: GET /api/users/1<br/>Authorization: Basic admin:admin
    G->>G: SecurityWebFilterChain: Basic auth<br/>(auditing manager publishes AUTHENTICATION_SUCCESS)
    alt bad / missing credentials
        G-->>C: 401 Unauthorized (+ AUTHENTICATION_FAILURE audit event)
    else authenticated
        G->>G: route match Path=/api/users/** -> lb://USER-SERVICE
        G->>G: ReactiveLoadBalancerClientFilter picks instance from local cache
        G->>U: GET /api/users/1<br/>Authorization header forwarded unchanged
        U->>U: servlet SecurityFilterChain: Basic auth
        U-->>G: 200 JSON
        G-->>C: 200 JSON
    end
```

<a id="actuator-endpoints"></a>
## <span style="color:hsl(33,80%,58%)">4. 📊 Actuator endpoints</span>

All exposed via `management.endpoints.web.exposure.include` in [application.yml](src/main/resources/application.yml). Only `health` and `info` are public; the rest need Basic auth. SBA (`admin-server`) authenticates with the `user.name` / `user.password` Eureka metadata.

| Endpoint | SBA view / action | Backing config in this module |
|---|---|---|
| `health` | Details / status | `show-details: always`; public |
| `info` | Details | public |
| `gateway` | Gateway (routes, global filters, route predicates) | `management.endpoint.gateway.access: unrestricted`; supplied by Spring Cloud Gateway |
| `env` | Environment (+ Update form) | `management.endpoint.env.post.enabled: true` (POST via Spring Cloud Context) |
| `refresh` | Environment "Refresh" | `access: unrestricted`; re-binds `@ConfigurationProperties` / `@RefreshScope` |
| `metrics` | Metrics | default Micrometer meters |
| `loggers` | Loggers (change levels live) | default |
| `threaddump` | Threads | default |
| `heapdump` | Heap Dump | `management.endpoint.heapdump.access: unrestricted` (Boot 3.5+ defaults to `none`) |
| `mappings` | Mappings | default (lists WebFlux handler mappings) |
| `beans` | Beans | default |
| `configprops` | Configuration Properties | default |
| `logfile` | Logfile | `logging.file.name: logs/${spring.application.name}.log` |
| `shutdown` | Shutdown button | `management.endpoint.shutdown.access: unrestricted` |
| `restart` | Restart button | `access: unrestricted`; endpoint from Spring Cloud Context |
| `httpexchanges` | HTTP Exchanges | `InMemoryHttpExchangeRepository` bean in [ActuatorConfig.java](src/main/java/com/learning/discovery/gateway/config/ActuatorConfig.java); recorded by `HttpExchangesWebFilter` (WebFlux variant) |
| `startup` | Startup | `BufferingApplicationStartup(2048)` set in `main()` of [GatewayServiceApplication.java](src/main/java/com/learning/discovery/gateway/GatewayServiceApplication.java) |
| `auditevents` | Audit Events | `InMemoryAuditEventRepository` bean + auditing auth manager ([§3](#security)) |
| `sbom` | SBOM | root `cyclonedx-maven-plugin` writes `META-INF/sbom/application.cdx.json` at build time |
| `quartz` | Quartz | `spring.quartz.job-store-type: memory` + `HeartbeatJob` ([§5](#quartz-logging-config)) |

- ***`startup` only exists when launched via `main()`*** — `@SpringBootTest` builds its own `SpringApplication` and skips `main()`, so there is no `BufferingApplicationStartup` and `/actuator/startup` is absent in tests.
- ***No `jolokia`*** — `jolokia-support-springboot` is servlet-only; the servlet modules (user/order/product/eureka) expose JMX via Jolokia, the WebFlux gateway (and `admin-server`) cannot, so SBA's JMX tab is missing here.
- ***Mutating endpoints*** — `shutdown`, `restart`, `refresh` and `env` POST change the running process; acceptable only because they sit behind Basic auth on a laptop.

```bash
curl -u admin:admin http://localhost:8080/actuator/gateway/routes
curl -u admin:admin "http://localhost:8080/actuator/auditevents?type=AUTHENTICATION_SUCCESS"
curl -u admin:admin http://localhost:8080/actuator/httpexchanges
curl -u admin:admin -o gateway.hprof http://localhost:8080/actuator/heapdump
curl -u admin:admin -X POST http://localhost:8080/actuator/refresh
```

### Health probes, info and tags

- ***Probes*** — `management.endpoint.health.probes.enabled: true` adds public `/actuator/health/liveness` and `/actuator/health/readiness` (SBA Details → Health groups).
- ***Info*** — `info.app.*` (name, description, `tier: edge`, environment) plus the `java`, `os`, `process` contributors (`management.info.*.enabled`) → SBA Details → Info.
- ***Tags*** — `eureka.instance.metadata-map.tags.environment` (`APP_ENV`, default `local`) and `tags.tier: edge` → badges in SBA's application list.
- Custom health indicators and metrics live in [order-service](../order-service/README.md) only.

<a id="quartz-logging-config"></a>
## <span style="color:hsl(171,80%,58%)">5. ⏱️ Quartz job, logging and configuration</span>

**Quartz demo job** ([job/](src/main/java/com/learning/discovery/gateway/job)):

- ***`HeartbeatJob`*** ([HeartbeatJob.java](src/main/java/com/learning/discovery/gateway/job/HeartbeatJob.java)) — extends `QuartzJobBean`, logs `Heartbeat from gateway-service (fire time ...)` at INFO.
- ***`QuartzConfig`*** ([QuartzConfig.java](src/main/java/com/learning/discovery/gateway/job/QuartzConfig.java)) — `JobDetail` `demo.heartbeatJob` (`storeDurably()`), `Trigger` `demo.heartbeatTrigger` with `SimpleScheduleBuilder.repeatMinutelyForever()`.
- ***In-memory store*** — `spring.quartz.job-store-type: memory`; nothing persisted, schedule resets on restart. The job exists purely so SBA's Quartz view has something to show.

**File logging:**

- ***`logging.file.name: logs/gateway-service.log`*** — relative to the process working directory; enables `/actuator/logfile`.
- ***Banner*** — [banner.txt](src/main/resources/banner.txt) prints "GATEWAY" plus project metadata and the gateway URL `http://localhost:${server.port}`.

**Annotated [application.yml](src/main/resources/application.yml) excerpt:**

<details><summary><h2><code><b>APPLICATION.YML — CLICK TO VIEW</b></code></h2></summary>

```yaml
spring:
  application:
    name: gateway-service            # Eureka app name GATEWAY-SERVICE
  security:
    user:
      name: ${ACTUATOR_USER:admin}
      password: ${ACTUATOR_PASSWORD:admin}
      roles: ACTUATOR
  quartz:
    job-store-type: memory           # backs /actuator/quartz
  cloud:
    gateway:
      server:
        webflux:                     # Spring Cloud 2025.x property prefix (was spring.cloud.gateway.routes)
          routes:                    # no RemoveRequestHeader: Authorization reaches the backend
            - id: user-service
              uri: lb://USER-SERVICE # service ID, resolved via Eureka + LoadBalancer
              predicates:
                - Path=/api/users/**
            # order-service, product-service: same shape

server:
  port: 8080

eureka:
  client:
    service-url:                     # Eureka server is Basic-secured too
      defaultZone: http://${spring.security.user.name}:${spring.security.user.password}@localhost:8761/eureka/
  instance:
    prefer-ip-address: true          # register IP, not hostname
    metadata-map:                    # SBA reads these to poll the secured actuator
      user.name: ${spring.security.user.name}
      user.password: ${spring.security.user.password}

logging:
  file:
    name: logs/${spring.application.name}.log   # /actuator/logfile

management:
  endpoint:
    gateway:
      access: unrestricted
    heapdump:
      access: unrestricted
    shutdown / restart / refresh:
      access: unrestricted           # SBA buttons (shown collapsed here)
    env:
      post:
        enabled: true
    health:
      show-details: always
```

</details>

<a id="tests"></a>
## <span style="color:hsl(0,80%,58%)">6. 🧪 Tests</span>

[GatewayServiceApplicationIT.java](src/test/java/com/learning/discovery/gateway/GatewayServiceApplicationIT.java) — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureWebTestClient` + `@ActiveProfiles("test")`; [application-test.yml](src/test/resources/application-test.yml) sets `eureka.client.enabled: false` so no registry is needed.

| Test | Checks |
|---|---|
| `healthEndpointReportsUpWithoutCredentials` | `GET /actuator/health` with no auth → 200, `$.status == "UP"` (permitAll works) |
| `protectedPathWithoutCredentialsIsUnauthorized` | `GET /actuator/env` with no auth → 401 |
| `protectedPathWithCredentialsIsAllowedAndAudited` | `GET /actuator/env` as `admin:admin` → 200; then `/actuator/auditevents?type=AUTHENTICATION_SUCCESS` returns `$.events[0].principal == "admin"` (auditing wrapper works) |
| `quartzHeartbeatJobIsRegistered` | `GET /actuator/quartz/jobs/demo/heartbeatJob` → 200, `$.name == "heartbeatJob"` |

- ***Not covered*** — actual proxying through `lb://` routes (would need registered backends or a stubbed `ServiceInstanceListSupplier`).
- ***Run*** — `mvn -pl gateway-service verify` (failsafe runs `*IT` in `integration-test`; see [root README §6](../README.md#tests)).

<a id="gotchas"></a>
## <span style="color:hsl(306,80%,58%)">7. ⚠️ Gotchas</span>

- ***503 Service Unavailable on `/api/**`*** — the gateway authenticated you but LoadBalancer found no instance for `USER-SERVICE` etc.: backend not started, not yet registered, or the gateway's registry cache hasn't refreshed (up to ~30s, plus Eureka's own response cache). Check http://localhost:8761 and retry.
- ***401 from the gateway*** — missing/wrong Basic creds, or `ACTUATOR_USER/ACTUATOR_PASSWORD` set differently from what the client sends.
- ***401 that came from the backend*** — gateway and backend run with different credentials; the forwarded header passes the gateway but fails downstream. Keep the env vars identical across modules.
- ***Eureka registration fails with 401*** — `eureka-server` uses the same creds; mismatched values break the `defaultZone` URL.
- ***Lombok is not on the classpath*** — unlike user/order/product, this module's [pom.xml](pom.xml) has no Lombok; use plain SLF4J `LoggerFactory` (as `HeartbeatJob` does), constructors and records.
- ***Changing `spring.application.name` breaks `lb://` for others, not for itself*** — renaming a backend (e.g. `user-service`) changes its Eureka ID, so `lb://USER-SERVICE` here resolves to nothing (503); renaming the gateway only changes its own registration and log file name.
- ***Servlet-only features don't work here*** — no Jolokia, no `spring-boot-starter-web`, no `HttpSecurity`; adding `spring-boot-starter-web` would conflict with the WebFlux gateway server.
- ***Old property prefix*** — Spring Cloud 2025.x (as of 2026) moved routes to `spring.cloud.gateway.server.webflux.routes`; copying older examples that use `spring.cloud.gateway.routes` is deprecated and should not be relied on.

<a id="qa"></a>
## <span style="color:hsl(116,80%,58%)">8. ❓ Q&A</span>

Questions go here.
