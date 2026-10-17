# <span style="color:hsl(201,80%,58%)">eureka-server-1</span>

## <span style="color:hsl(151,80%,58%)">Table of contents</span>

1. 🧭 [Overview](#overview)
2. 🚀 [Run it](#run-it)
3. 🔐 [Security](#security)
4. 📊 [Actuator endpoints and SBA views](#actuator-endpoints)
5. ⏱️ [Quartz demo job, file logging, configuration](#quartz-logging-config)
6. 🧪 [Tests](#tests)
7. ⚠️ [Gotchas](#gotchas)
8. ❓ [Q&A](#qa)

Peer 1 of the two-node service registry for the [learning-service-discovery](../README.md) demo; [`eureka-server-2`](../eureka-server-2/README.md) is the same code on port 8762 (this README covers both). Every other module (`user-service`, `order-service`, `product-service`, `gateway-service`, `admin-server`) registers here. For how the gateway resolves `lb://` IDs against this registry, how Spring Boot Admin discovers instances, and the FAQ on AP/CAP, self-preservation and eviction timing, see the [root README](../README.md#faq).

<a id="overview"></a>
## <span style="color:hsl(288,80%,58%)">1. 🧭 Overview</span>

| Item | Value |
|---|---|
| Role | Netflix Eureka ***service registry*** + dashboard |
| Port | `8761` ([`application.yml`](src/main/resources/application.yml) `server.port`); `eureka-server-2`: `8762` |
| Web stack | Servlet (Spring MVC / Tomcat) |
| Stack (as of 2026) | Spring Boot 4.1.1, Spring Cloud 2025.1.x (banner says 2025.1.2), Java 27 |
| Main class | [`EurekaServerApplication`](src/main/java/com/learning/discovery/eureka/EurekaServerApplication.java) — `@EnableEurekaServer` + `@SpringBootApplication` |
| Dependencies ([`pom.xml`](pom.xml)) | `spring-cloud-starter-netflix-eureka-server`, `spring-boot-starter-actuator`, `spring-boot-starter-security`, `spring-boot-starter-quartz`, `jolokia-support-springboot` 2.6.3; tests: `spring-boot-starter-test`, `spring-security-test` |

- ***Two peer modules*** — `eureka-server-1` (:8761) and `eureka-server-2` (:8762) are copies that differ only in `server.port`, `eureka.instance.hostname`, the peer URL in `defaultZone`, the log file name and the banner (`diff -r eureka-server-1/src eureka-server-2/src`). Each lists the *other* in `defaultZone`, so every register/heartbeat/cancel one receives is replicated to the other. ***Change both*** when editing code or config. Details in [Run both peers](#run-peers).
- ***Registers with its peer*** — `eureka.client.register-with-eureka: true` so `admin-server` (which discovers apps *from* Eureka) lists `EUREKA-SERVER` (2 instances) alongside the other services. Both keep `spring.application.name: eureka-server`: the dashboard counts a peer as `available-replicas` only if it finds it among *its own* app's instances.
- ***Fetch the registry: only as a peer*** — standalone keeps `fetch-registry: false` (this node *is* the registry); the peer profiles set it `true`, because on startup a node copies its initial registry from the other peer through its own Eureka client — without it a restarted peer comes up empty.
- ***Self-preservation: on*** — `enable-self-preservation: ${EUREKA_SELF_PRESERVATION:true}`, so the dashboard no longer shows `THE SELF PRESERVATION MODE IS TURNED OFF...` and a network blip can't mass-evict the registry. Threshold `EUREKA_RENEWAL_THRESHOLD` defaults to `0.5` locally (killing one or two services in a ~8-instance demo still evicts them) and `0.85` under the `prod` profile ([application-prod.yml](src/main/resources/application-prod.yml)). Tradeoffs in the root [FAQ](../README.md#faq).
- ***Faster leases (all modules)*** — clients heartbeat every 10 s and expire 30 s after the last one (`lease-renewal-interval-in-seconds` / `lease-expiration-duration-in-seconds`, defaults 30 / 90); the server matches with `expected-client-renewal-interval-seconds: 10` (so the self-preservation threshold is computed correctly), sweeps every 30 s.
- ***Dashboard header*** — `eureka.environment: ${EUREKA_ENVIRONMENT:sit}` and `eureka.datacenter: ${EUREKA_DATACENTER:TX}` replace the defaults `test` / `default` shown as "Environment" / "Data center" on the dashboard. Labels only — they change no registry behaviour.
- ***No other eviction tuning*** — lease renewal/expiry and the eviction timer run on Eureka defaults; only self-preservation is overridden.
- ***Hostname*** — `eureka-server-1` is `localhost`, `eureka-server-2` is `127.0.0.1`: peers need distinct hostnames, and these two resolve everywhere without an `/etc/hosts` entry.
- ***Startup banner*** — [`banner.txt`](src/main/resources/banner.txt) prints project metadata and the dashboard URL.

<a id="run-it"></a>
## <span style="color:hsl(66,80%,50%)">2. 🚀 Run it</span>

Start it ***first*** — every other module needs somewhere to register (full order in the root [Run order](../README.md#run-order)).

```bash
mvn -pl eureka-server-1 spring-boot:run &
mvn -pl eureka-server-2 spring-boot:run &
# or with custom credentials (same on both, and on every client)
ACTUATOR_USER=ops ACTUATOR_PASSWORD=s3cret mvn -pl eureka-server-1 spring-boot:run
```

| What | URL | Auth |
|---|---|---|
| Dashboard | http://localhost:8761/ | `admin`/`admin` (browser login form or Basic) |
| Registry REST API | http://localhost:8761/eureka/apps | Basic |
| One app | http://localhost:8761/eureka/apps/USER-SERVICE | Basic |
| Health | http://localhost:8761/actuator/health | public |
| Info | http://localhost:8761/actuator/info | public |

```bash
curl -u admin:admin -H 'Accept: application/json' http://localhost:8761/eureka/apps
curl http://localhost:8761/actuator/health
```

<a id="run-peers"></a>
### Run both peers

```bash
mvn -pl eureka-server-1 spring-boot:run &   # :8761
mvn -pl eureka-server-2 spring-boot:run &   # :8762
```

- ***Clients list both*** — every module's `defaultZone` is `...@localhost:8761/eureka/,...@127.0.0.1:8762/eureka/`; a client talks to the first reachable one and fails over to the next. `zipkin-server` takes a single URI only, so it points at `eureka-server-1` (`localhost:8761`).
- ***Check replication*** — each dashboard's *DS Replicas* shows the other node, and *General Info* lists it under `available-replicas`; an app that registers with one node appears on both; `EUREKA-SERVER` shows 2 instances.
- ***Startup noise*** — whichever peer starts first logs `Connection refused` for the other until it's up; harmless, it retries every 10–30 s.
- ***Log files*** — `logs/eureka-server-1.log` / `logs/eureka-server-2.log`.

- ***Credentials*** — `spring.security.user.name`/`password` default to `admin`/`admin`, overridable via env vars `ACTUATOR_USER` / `ACTUATOR_PASSWORD`. The user gets role `ACTUATOR`.
- ***Clients must use the same values*** — every module shares this one user, so if you override them here, override them for all modules.

<a id="security"></a>
## <span style="color:hsl(118,80%,58%)">3. 🔐 Security</span>

Defined in [`SecurityConfig`](src/main/java/com/learning/discovery/eureka/config/SecurityConfig.java) as a single servlet `SecurityFilterChain`:

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info", "/error").permitAll()
        .anyRequest().authenticated())
    .httpBasic(Customizer.withDefaults())
    .formLogin(Customizer.withDefaults())
    .csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**", "/actuator/**"))
    .build();
```

| Rule | Paths | Why |
|---|---|---|
| `permitAll` | `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/error` | Liveness/readiness probes and error pages work without credentials |
| `authenticated` | everything else — dashboard `/`, `/eureka/**`, other actuator endpoints | Registry and ops endpoints are not public |
| HTTP Basic | all | Machine clients (Eureka clients, SBA, `curl`) |
| Form login | all | Browser hitting the dashboard gets Spring Security's default `/login` page |
| CSRF ignored | `/eureka/**`, `/actuator/**` | See below |

- ***Why CSRF is off for `/eureka/**`*** — Eureka clients `POST` (register), `PUT` (heartbeat/status) and `DELETE` (deregister) against the REST API with Basic auth only; they never fetch a CSRF token, so with CSRF on every write would be rejected with 403 and nothing could register.
- ***Why CSRF is off for `/actuator/**`*** — SBA invokes write operations (`POST /actuator/shutdown`, `/restart`, `/refresh`, `/env`, `/loggers/...`, Jolokia) server-to-server with Basic auth and no token.
- ***CSRF stays on for the dashboard/form-login paths*** — the browser session flow keeps normal protection.
- ***Clients authenticate via `defaultZone`*** — credentials go in the URL userinfo; the Eureka client turns them into a Basic header:

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://admin:admin@localhost:8761/eureka/
```

- ***This node uses the same trick for its self-registration*** — its own `defaultZone` is `http://${spring.security.user.name}:${spring.security.user.password}@${eureka.instance.hostname}:${server.port}/eureka/`.
- ***SBA credentials via metadata*** — `eureka.instance.metadata-map.user.name` / `user.password` publish the actuator credentials in the registry entry; `admin-server` reads them and uses them as Basic auth when polling `/actuator/**` (fallback: `spring.boot.admin.instance-auth.default-*` — root [§10](../README.md#security-and-sba-features)).

```mermaid
sequenceDiagram
    participant Client as Eureka client (e.g. user-service)
    participant Eureka as eureka-server :8761
    participant SBA as admin-server :8084

    Client->>Eureka: POST /eureka/apps/USER-SERVICE (Basic admin:admin, no CSRF token)
    Eureka-->>Client: 204 registered
    loop every 30s
        Client->>Eureka: PUT /eureka/apps/USER-SERVICE/{instanceId} (heartbeat)
        Eureka-->>Client: 200 lease renewed
    end
    SBA->>Eureka: GET /eureka/apps (Basic)
    Eureka-->>SBA: instances + metadata (user.name, user.password)
    SBA->>Eureka: GET /actuator/** on eureka-server itself (Basic from metadata)
    SBA->>Client: GET /actuator/** (Basic from that instance's metadata)
    Note over Client,Eureka: Without credentials in defaultZone: 401, client never appears
```

<a id="actuator-endpoints"></a>
## <span style="color:hsl(256,80%,58%)">4. 📊 Actuator endpoints and SBA views</span>

All exposed under `/actuator/` via `management.endpoints.web.exposure.include`; all except `health` and `info` require Basic auth. `health.show-details: always`.

| Endpoint | SBA view / action | Backing config in this module |
|---|---|---|
| `health` | Health (details shown) | `management.endpoint.health.show-details: always`; public |
| `info` | Info | public |
| `heapdump` | Heap Dump | `management.endpoint.heapdump.access: unrestricted` (Boot 3.5+ defaults to `none`, as of 2026) |
| `logfile` | Logfile | `logging.file.name: logs/${spring.application.name}.log` |
| `env` | Environment | exposed; `management.endpoint.env.post.enabled: true` enables the SBA "Update" form (POST, Spring Cloud Context) |
| `shutdown` | Shutdown button | `management.endpoint.shutdown.access: unrestricted` |
| `restart` | Restart button | `management.endpoint.restart.access: unrestricted` (Spring Cloud Context) |
| `refresh` | Environment "Refresh" | `management.endpoint.refresh.access: unrestricted` — rebinds `@ConfigurationProperties` / `@RefreshScope` |
| `metrics` | Metrics / Details | exposed, Micrometer defaults |
| `loggers` | Loggers (change levels at runtime) | exposed |
| `threaddump` | Threads | exposed |
| `configprops` | Configuration Properties | exposed |
| `beans` | Beans | exposed |
| `mappings` | Mappings | exposed |
| `httpexchanges` | HTTP Exchanges | `InMemoryHttpExchangeRepository` bean in [`ActuatorConfig`](src/main/java/com/learning/discovery/eureka/config/ActuatorConfig.java) |
| `startup` | Startup | `BufferingApplicationStartup(2048)` set in `main()` of [`EurekaServerApplication`](src/main/java/com/learning/discovery/eureka/EurekaServerApplication.java) — ***only when launched via `main`*** |
| `auditevents` | Audit Events | `InMemoryAuditEventRepository` bean in [`ActuatorConfig`](src/main/java/com/learning/discovery/eureka/config/ActuatorConfig.java) + Spring Security authentication events |
| `sbom` | SBOM | root [`pom.xml`](../pom.xml) `cyclonedx-maven-plugin` writes `META-INF/sbom/application.cdx.json` at build time |
| `quartz` | Quartz | `spring-boot-starter-quartz`, in-memory store, demo `HeartbeatJob` (§5) |
| `jolokia` | JMX | `jolokia-support-springboot` 2.6.3 (servlet only, supports Boot 4 as of 2026) |

- ***`shutdown`, `restart`, `env` POST and Jolokia mutate the running registry*** — acceptable behind Basic auth on localhost; never expose them on a public port.

### Health probes, info and tags

- ***Probes*** — `management.endpoint.health.probes.enabled: true` adds public `/actuator/health/liveness` and `/actuator/health/readiness` (SBA Details → Health groups).
- ***Info*** — `info.app.*` (name, description, `tier: infra`, environment) plus the `java`, `os`, `process` contributors (`management.info.*.enabled`) → SBA Details → Info.
- ***Tags*** — `eureka.instance.metadata-map.tags.environment` (`APP_ENV`, default `local`) and `tags.tier: infra` → badges in SBA's application list.
- Custom health indicators and metrics live in [order-service](../order-service/README.md) only.

<a id="quartz-logging-config"></a>
## <span style="color:hsl(33,80%,58%)">5. ⏱️ Quartz demo job, file logging, configuration</span>

**Quartz** — exists only so the SBA Quartz view has data:

| Piece | File | Detail |
|---|---|---|
| Job | [`HeartbeatJob`](src/main/java/com/learning/discovery/eureka/job/HeartbeatJob.java) | `QuartzJobBean`; logs `Heartbeat from eureka-server (next fire: ...)` |
| `JobDetail` | [`QuartzConfig`](src/main/java/com/learning/discovery/eureka/config/QuartzConfig.java) | identity `heartbeatJob` / group `eureka`, description "Logs a heartbeat line every minute", `storeDurably()` |
| `Trigger` | [`QuartzConfig`](src/main/java/com/learning/discovery/eureka/config/QuartzConfig.java) | identity `heartbeatTrigger` / group `eureka`, `SimpleScheduleBuilder.repeatMinutelyForever()` |
| Store | [`application.yml`](src/main/resources/application.yml) | `spring.quartz.job-store-type: memory` (RAMJobStore) |

- ***No scheduler wiring code*** — Boot auto-registers every `JobDetail` and `Trigger` bean with the auto-configured scheduler.
- ***Not to be confused with Eureka client heartbeats*** — this job only writes a log line.

**File logging** — `logging.file.name: logs/eureka-server.log` (peers: `eureka-server-1.log` / `-2.log`) (relative to the working directory) is what switches on `/actuator/logfile`.

**Annotated [`application.yml`](src/main/resources/application.yml) excerpt:**

```yaml
spring:
  application:
    name: eureka-server
  security:
    user:
      name: ${ACTUATOR_USER:admin}          # shared demo user
      password: ${ACTUATOR_PASSWORD:admin}
      roles: ACTUATOR
  quartz:
    job-store-type: memory                  # RAMJobStore for HeartbeatJob

server:
  port: 8761

eureka:
  instance:
    hostname: localhost
    metadata-map:                           # SBA reads these to poll /actuator
      user.name: ${spring.security.user.name}
      user.password: ${spring.security.user.password}
  client:
    register-with-eureka: true              # appear in SBA
    fetch-registry: false                   # this node is the registry
    service-url:
      defaultZone: http://${spring.security.user.name}:${spring.security.user.password}@${eureka.instance.hostname}:${server.port}/eureka/
  server:
    enable-self-preservation: true          # no mass eviction on a network blip; threshold 0.5 locally

logging:
  file:
    name: logs/${spring.application.name}.log   # backs /actuator/logfile

management:
  endpoint:
    shutdown: { access: unrestricted }
    restart:  { access: unrestricted }
    refresh:  { access: unrestricted }
    env:      { post: { enabled: true } }
    health:   { show-details: always }
    heapdump: { access: unrestricted }
```

<a id="tests"></a>
## <span style="color:hsl(171,80%,58%)">6. 🧪 Tests</span>

[`EurekaServerApplicationTests`](src/test/java/com/learning/discovery/eureka/EurekaServerApplicationTests.java) — `@SpringBootTest(webEnvironment = RANDOM_PORT)` driven by `RestTestClient` against the real embedded server.

| Test | Checks |
|---|---|
| `contextLoads` | `@EnableEurekaServer` context boots without errors |
| `dashboardIsServedAtRootWithCredentials` | `GET /` with Basic `admin:admin` → 2xx and body contains `Eureka` |
| `healthEndpointIsPublicAndReportsUp` | `GET /actuator/health` without auth → 200, `$.status == "UP"` |
| `registryApiRejectsAnonymousCallers` | `GET /eureka/apps` without auth → 401 |
| `registryApiAcceptsBasicAuth` | `GET /eureka/apps` with Basic → 200 |
| `sbaEndpointsAreExposedBehindAuth` | `/actuator/quartz` anonymous → 401; `quartz`, `httpexchanges`, `auditevents`, `jolokia`, `sbom` with Basic → 200 |

- ***Why `register-with-eureka=false` and `fetch-registry=false` in the test*** — `defaultZone` points at the other peer, which isn't running during the test; registering or fetching would just spam connection errors. The test boots the node standalone.
- ***Why `startup` isn't tested*** — `BufferingApplicationStartup` is installed only in `main()`; `@SpringBootTest` doesn't run `main`, so the endpoint isn't there.

```bash
mvn -pl eureka-server-1,eureka-server-2 test
```

<a id="gotchas"></a>
## <span style="color:hsl(308,80%,58%)">7. ⚠️ Gotchas</span>

- ***Start it first*** — clients started before it log connection-refused retries and only appear once they retry successfully.
- ***Clients get 401 without credentials*** — a `defaultZone` of `http://localhost:8761/eureka/` (no `user:pass@`) fails registration silently from the dashboard's point of view; the client logs 401s and never shows up.
- ***Mismatched credentials*** — overriding `ACTUATOR_USER`/`ACTUATOR_PASSWORD` here without doing the same on the clients has the same 401 effect.
- ***Shutting it down*** — `POST /actuator/shutdown` (or the SBA button) kills the registry: clients keep serving from their cached registry copy, but new registrations and heartbeats fail and `admin-server` loses discovery until it is back.
- ***Password visible in metadata*** — `metadata-map.user.password` is readable in plain text by anyone who can `GET /eureka/apps` (and in the dashboard). Fine for a local demo; use per-service secrets or SBA's `instance-auth` in anything real.
- ***Self-preservation is on*** — a crashed (not gracefully stopped) instance lingers if renewals drop below the threshold; the dashboard then shows `EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP...`. Graceful stops send a cancel and vanish immediately. Briefly after startup the same banner can appear until a full minute of heartbeats is counted.
- ***Heartbeat `400` / `Root name ('timestamp') does not match expected ('instance')`*** — a node that doesn't know an instance answers its heartbeat with `404`, and the client re-registers. Out of the box Jersey sends that 404 via `sendError()`, Spring Boot's `/error` page adds a JSON body, the client can't parse it as `InstanceInfo` and never re-registers. [`JerseyConfig`](src/main/java/com/learning/discovery/eureka/config/JerseyConfig.java) sets Jersey's `setStatusOverSendError` so the 404 stays bare (test `heartbeatForUnknownInstanceIsBare404`).
- ***Peers need distinct hostnames*** — Eureka compares only the host of a peer URL with its own `eureka.instance.hostname` (not the port); two nodes both named `localhost` treat each other as "me" and never replicate. Hence `localhost` vs `127.0.0.1` — don't "tidy" them to the same name.
- ***`logs/` is relative*** — the log file lands under whatever directory you launched from.

<a id="qa"></a>
## <span style="color:hsl(223,80%,58%)">8. ❓ Q&A</span>

Questions go here.
