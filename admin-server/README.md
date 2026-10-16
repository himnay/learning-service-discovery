# <span style="color:hsl(201,80%,58%)">admin-server</span>

## <span style="color:hsl(151,80%,58%)">Table of contents</span>

1. 🧩 [Overview](#overview)
2. 🚀 [Run and log in](#run-and-login)
3. 🔐 [Security in detail](#security)
4. 🔑 [Alternative login options (as of 2026)](#login-options)
5. 📡 [Actuator endpoints exposed](#actuator-endpoints)
6. ⏱️ [Quartz demo job](#quartz)
7. 📝 [Logging to file](#logging)
8. ⚙️ [Configuration reference](#configuration)
9. 🧪 [Tests](#tests)
10. 🛠️ [Gotchas and troubleshooting](#gotchas)
11. ❓ [Q&A](#qa)

Spring Boot Admin (SBA) dashboard for the Eureka demo. Back to the project
overview: [../README.md](../README.md).

<a id="overview"></a>
## <span style="color:hsl(288,80%,58%)">1. 🧩 Overview</span>

- ***Role: a separate observer, not in the request path.*** It shows health,
  metrics, logs, environment, beans and so on for every instance registered in
  Eureka, and lets you act on them (change log levels, refresh, restart, shut down).
- ***Port `8084`***, dashboard at http://localhost:8084.
- ***Reactive stack.*** The SBA 4 server starter runs on WebFlux/Netty, so
  everything here (security, filters, tests) is the reactive variant.
- ***Finds apps through Eureka, not by self-registration.*** With
  `spring-cloud-starter-netflix-eureka-client` on the classpath, SBA uses the
  Spring Cloud `DiscoveryClient` to list every service in the registry and turns
  each one into an SBA *instance*. The services don't need the SBA client library.
- ***It registers itself too.*** admin-server is a Eureka client
  (`eureka.client.service-url.defaultZone` → `localhost:8761`), so it appears in
  its own dashboard as `ADMIN-SERVER`, next to `EUREKA-SERVER`, `USER-SERVICE`,
  `ORDER-SERVICE`, `PRODUCT-SERVICE` and `GATEWAY-SERVICE`.
- Entry point: [AdminServerApplication.java](src/main/java/com/learning/discovery/admin/AdminServerApplication.java),
  annotated with `@EnableAdminServer` + `@SpringBootApplication`.

| Component | Version (as of 2026) | Where it comes from |
|---|---|---|
| Java | 27 | `super-pom` parent |
| Spring Boot | 4.1.1 | `super-pom` parent |
| Spring Cloud | 2025.1.x | `super-pom` BOM import |
| Spring Boot Admin | 4.1.x (`spring-boot-admin-starter-server`) | `super-pom` BOM import |
| Quartz | Boot-managed (`spring-boot-starter-quartz`) | Boot BOM |

Dependencies in [pom.xml](pom.xml):

| Dependency | Why |
|---|----|
| `spring-boot-admin-starter-server` | the SBA server and Vue UI |
| `spring-cloud-starter-netflix-eureka-client` | discovery of instances + self-registration; also brings Spring Cloud Context (`restart`, `refresh`, `env` POST) |
| `spring-boot-starter-actuator` | admin-server's own actuator endpoints |
| `spring-boot-starter-security` | login for the UI and actuator |
| `spring-boot-starter-quartz` | demo job behind the SBA "Quartz" view |
| `spring-boot-starter-test`, `spring-boot-webtestclient`, `spring-boot-starter-security-test` | integration test ([§9](#tests)) |

- ***No Jolokia***: the pom comments it out on purpose. `jolokia-support-springboot`
  needs a servlet container, and this server is WebFlux only.

<a id="run-and-login"></a>
## <span style="color:hsl(66,80%,50%)">2. 🚀 Run and log in</span>

- ***Start `eureka-server` first.*** admin-server starts without it, but shows no
  applications until it can fetch the registry.
- From the repo root:

```bash
cd learning-service-discovery
mvn -pl eureka-server spring-boot:run &
# wait for http://localhost:8761, then:
mvn -pl admin-server spring-boot:run
```

- ***IntelliJ***: run `AdminServerApplication` (its `main` is package-private
  and `static void main` with no `public`, which Java 25+ allows).
- Open http://localhost:8084. You are redirected to `/login`.
- ***Credentials: `admin` / `admin`.*** Override them with environment variables:

```bash
ACTUATOR_USER=ops ACTUATOR_PASSWORD='s3cret' mvn -pl admin-server spring-boot:run
```

- ***Use the same values for every module.*** The same user is used for the UI
  login, admin-server's own actuator, the Eureka registry URL, and the Basic
  auth SBA sends to each service. If they differ, instances show up as
  `OFFLINE` or never appear at all.

<a id="security"></a>
## <span style="color:hsl(31,80%,58%)">3. 🔐 Security in detail</span>

Everything lives in [SecurityConfig.java](src/main/java/com/learning/discovery/admin/config/SecurityConfig.java).
It is SBA's documented "secure the server" setup, rewritten for WebFlux.

### <span style="color:hsl(339,80%,58%)">3.1 Filter chain</span>

- ***A reactive `SecurityWebFilterChain`, with `@EnableWebFluxSecurity`.*** A
  servlet `SecurityFilterChain` would be silently ignored on Netty.
- ***The user store is Boot's default `ReactiveUserDetailsService`***, a
  single in-memory user built from `spring.security.user.*` (role `ACTUATOR`).
  It is wrapped in `UserDetailsRepositoryReactiveAuthenticationManager`, then in
  an auditing decorator ([§3.4](#security)).
- All paths are prefixed with `AdminServerProperties.path(...)`, so the setup
  keeps working if `spring.boot.admin.context-path` is ever set.

| Rule | Paths | Effect |
|---|---|---|
| `permitAll` | `/assets/**`, `/variables.css`, `/login` | the login page and its CSS/JS load before you log in |
| `permitAll` | `/actuator/health/**`, `/actuator/info`, `/error` | probes and the Eureka/SBA status checks need no credentials |
| `authenticated` | everything else | UI, SBA REST API (`/applications`, `/instances`), other actuator endpoints |

| Mechanism | Config | Behaviour |
|---|---|---|
| ***Form login*** | `loginPage(/login)`, success → `RedirectServerAuthenticationSuccessHandler("/")` | browser requests (`Accept: text/html`) without a session get a `302` to `/login`; a successful login goes to `/` |
| ***Logout*** | `logoutUrl(/logout)`, success → `/login?logout` | ends the session and shows SBA's "logged out" message |
| ***HTTP Basic*** | `httpBasic(...)` with the same auditing manager | non-HTML clients (curl, JSON API) get a `401` + `WWW-Authenticate: Basic` |

### <span style="color:hsl(116,80%,58%)">3.2 CSRF</span>

- ***The token is kept in a cookie the JS can read:***
  `CookieServerCsrfTokenRepository.withHttpOnlyFalse()` writes `XSRF-TOKEN`;
  the SBA Vue app (axios) sends it back as the `X-XSRF-TOKEN` header.
- ***`ServerCsrfTokenRequestAttributeHandler` (plain, not XOR-masked)***, so the raw cookie value is
  accepted as both header and `_csrf` form field, which is what the SBA UI sends.
- ***Exempt paths***, which are combined with `CsrfWebFilter.DEFAULT_CSRF_MATCHER` (so GET/HEAD/OPTIONS/TRACE
  are never checked):

| Exempt path | Why |
|---|---|
| `/instances`, `/instances/**` | SBA clients self-register here with a plain POST, so there's no browser session or token |
| `/actuator/**` | tooling and SBA calls with Basic auth (e.g. `POST /actuator/refresh`) |

- ***`csrfCookieWebFilter` bean.*** On WebFlux the CSRF token is a deferred
  `Mono<CsrfToken>`, and the cookie is only written if something subscribes to
  it. This lowest-precedence `WebFilter` subscribes on every request, so the SPA
  always has an `XSRF-TOKEN` cookie.
- ***[CsrfControllerAdvice.java](src/main/java/com/learning/discovery/admin/config/CsrfControllerAdvice.java).***
  SBA's Thymeleaf templates (`login.html`, `sba-settings.js`) read `${_csrf}`.
  Servlet security exposes that as a request attribute on its own; WebFlux does not.
  The `@ControllerAdvice` adds the `Mono<CsrfToken>` to the model as `_csrf`.
  ***Without it the login form posts with no token and gets a 403.***

### <span style="color:hsl(254,80%,58%)">3.3 Remember-me is disabled</span>

- ***Reactive Spring Security has no `rememberMe()`*** DSL, so there is nothing
  to back the checkbox.
- `spring.boot.admin.ui.remember-me-enabled: false` hides the checkbox in the
  SBA login page, so users aren't offered a feature that doesn't work.

### <span style="color:hsl(169,80%,58%)">3.4 Audit events for logins</span>

- ***Reactive security publishes no authentication events by default*** (the
  servlet `ProviderManager` does). So Boot's `AuthenticationAuditListener` would
  never see a login.
- `auditing(...)` wraps the manager with a `DefaultAuthenticationEventPublisher`:
  `doOnNext` → `publishAuthenticationSuccess`, `doOnError(AuthenticationException)`
  → `publishAuthenticationFailure`.
- The listener turns these into `AUTHENTICATION_SUCCESS` / `AUTHENTICATION_FAILURE`
  audit events, stored in the `InMemoryAuditEventRepository` from
  [ActuatorConfig.java](src/main/java/com/learning/discovery/admin/config/ActuatorConfig.java)
  and shown in SBA's "Audit Log" view (`/actuator/auditevents`).
- This covers both form logins and Basic logins, because both use the same wrapped manager.

### <span style="color:hsl(306,80%,58%)">3.5 How SBA authenticates to each service</span>

- Every service's actuator (except health/info) requires Basic auth, so SBA has
  to send credentials when it polls them.
- ***Per-instance metadata wins.*** SBA's `BasicAuthHttpHeaderProvider` reads
  `user.name` / `user.password` from the instance metadata. With Eureka that is
  each service's `eureka.instance.metadata-map`, which is copied into the
  registry and then into SBA. admin-server publishes its own metadata, because
  SBA discovers itself as well.
- ***Fallback: `spring.boot.admin.instance-auth`*** with `enabled: true` and
  `default-user-name` / `default-password` set to `spring.security.user.*`.
  This is used for any instance without metadata credentials.
- The header goes on every SBA→instance call: status polling, the endpoint
  detection probe, and the actuator calls the UI proxies (`/instances/{id}/actuator/**`).

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant SBA as admin-server :8084
    participant E as eureka-server :8761
    participant S as user-service :8081
    B->>SBA: GET / (text/html)
    SBA-->>B: 302 /login (+ XSRF-TOKEN cookie)
    B->>SBA: GET /login
    SBA-->>B: login.html with _csrf (CsrfControllerAdvice)
    B->>SBA: POST /login username, password, _csrf
    SBA->>SBA: auditing manager → AUTHENTICATION_SUCCESS event
    SBA-->>B: 302 / (SESSION cookie)
    loop every registry refresh
        SBA->>E: GET /eureka/apps (Basic admin:admin)
        E-->>SBA: instances + metadata-map (user.name, user.password)
    end
    B->>SBA: GET /instances/{id}/actuator/metrics (session + X-XSRF-TOKEN)
    SBA->>S: GET /actuator/metrics (Authorization: Basic from metadata)
    S-->>SBA: 200 JSON
    SBA-->>B: 200 JSON → rendered in UI
```

<a id="login-options"></a>
## <span style="color:hsl(84,80%,58%)">4. 🔑 Alternative login options (as of 2026)</span>

How *people* log into the dashboard. All of them must be the reactive variant,
because this is WebFlux.

| Option | How on WebFlux | Notes |
|---|---|---|
| ***In-memory single user (current)*** | Boot's default from `spring.security.user.*` | fine for a demo; one shared password, no per-person audit |
| Multiple in-memory users | `MapReactiveUserDetailsService` bean with `User.withUsername(..).password(bcryptEncoder.encode(..))` + a `PasswordEncoder` bean (`BCryptPasswordEncoder` / `DelegatingPasswordEncoder`) | still in config, but gives per-person audit events and roles |
| Database users | custom `ReactiveUserDetailsService` backed by R2DBC (`spring-boot-starter-data-r2dbc`) | stay fully non-blocking; JPA/JDBC would block the event loop |
| LDAP / Active Directory | `ReactiveAuthenticationManagerAdapter` wrapping a blocking `LdapAuthenticationProvider` / `ActiveDirectoryLdapAuthenticationProvider` | ***blocking caveat***: the adapter runs it on `Schedulers.boundedElastic()`. That works, but it isn't truly reactive |
| ***OAuth2 / OIDC login*** | `spring-boot-starter-oauth2-client` + `http.oauth2Login(...)`; IdP: Keycloak, Okta, Microsoft Entra ID, Google, GitHub (GitHub is OAuth2 only, no OIDC) | ***recommended for production***: SSO, MFA and group→role mapping are handled by the IdP. SBA's own `/login` page is replaced by the IdP redirect |
| SAML2 | `saml2Login()` exists for ***servlet only*** | ***not available on WebFlux***. Use OIDC, or put a SAML-capable proxy in front |
| X.509 client certificates (mTLS) | `server.ssl.client-auth=need` + `http.x509(...)` | good for machine access or locked-down admin networks; awkward in browsers |
| Remember-me | — | ***not on WebFlux*** (no reactive `rememberMe()`); use longer session timeouts or IdP SSO instead |

How *SBA* authenticates to each service:

| Option | How | Notes |
|---|---|---|
| ***Metadata Basic auth (current)*** | each service sets `eureka.instance.metadata-map.user.name/user.password`; `BasicAuthHttpHeaderProvider` sends it | simple, but the password sits in the Eureka registry, so anyone who can read `/eureka/apps` can see it |
| Per-service defaults | `spring.boot.admin.instance-auth.service-map.<service-name>.user-name/user-password` | keeps credentials out of the registry; they're configured centrally in admin-server |
| ***OAuth2 client credentials (bearer)*** | custom `HttpHeadersProvider` bean that gets a token through `ReactiveOAuth2AuthorizedClientManager` (client-credentials grant) and sets `Authorization: Bearer ...`; each service becomes a JWT resource server (`spring-boot-starter-oauth2-resource-server`, `http.oauth2ResourceServer(jwt())`) that requires an `actuator` scope | no shared passwords; tokens expire and can be revoked; per-service scopes |
| mTLS | SBA's `WebClient` configured with a client key store; services require client certs on the management port | strongest transport identity; you have to run a certificate PKI |

- ***Recommendation for production***: ***Keycloak (or any OIDC IdP) for
  dashboard login*** + ***OAuth2 client credentials*** for SBA→service calls.
  That gives one identity system, short-lived tokens, and no static passwords in
  Eureka metadata.

<a id="actuator-endpoints"></a>
## <span style="color:hsl(201,80%,58%)">5. 📡 Actuator endpoints exposed</span>

admin-server's own actuator endpoints. SBA shows them for the `ADMIN-SERVER`
instance, just as it does for the other services. All of them except
`health`/`info` need authentication.

| Endpoint | SBA view | Backed by |
|---|---|---|
| `health` | Details → Health (`show-details: always`) | Boot auto-config (Eureka, disk, ping, ...) |
| `info` | Details → Info | Boot auto-config |
| `metrics` | Details → Metrics, JVM, process | Micrometer |
| `env` (GET + ***POST***) | Environment (+ "Update" form) | `management.endpoint.env.post.enabled: true`, Spring Cloud Context |
| `refresh` (POST) | Environment → "Refresh" | Spring Cloud Context; `access: unrestricted`; re-binds `@ConfigurationProperties` / `@RefreshScope` |
| `restart` (POST) | "Restart" button | Spring Cloud Context; `access: unrestricted` |
| `shutdown` (POST) | "Shutdown" button | `access: unrestricted` |
| `loggers` | Loggers (change levels at runtime) | Boot auto-config |
| `logfile` | Logfile | `logging.file.name` ([§7](#logging)) |
| `heapdump` | Heap Dump (download) | `access: unrestricted`; still behind login |
| `threaddump` | Thread Dump | Boot auto-config |
| `configprops` | Configuration Properties | Boot auto-config |
| `beans` | Beans | Boot auto-config |
| `mappings` | Mappings | Boot auto-config (WebFlux handler mappings) |
| `httpexchanges` | HTTP Exchanges | `InMemoryHttpExchangeRepository` bean in [ActuatorConfig.java](src/main/java/com/learning/discovery/admin/config/ActuatorConfig.java), recorded by `HttpExchangesWebFilter` |
| `startup` | Startup | `BufferingApplicationStartup(2048)` set in [AdminServerApplication.java](src/main/java/com/learning/discovery/admin/AdminServerApplication.java) `main` |
| `auditevents` | Audit Log | `InMemoryAuditEventRepository` bean + the login auditing in [§3.4](#security) |
| `sbom` | SBOM | `META-INF/sbom/application.cdx.json` written by the `cyclonedx-maven-plugin` declared in the [root pom](../pom.xml) |
| `quartz` | Quartz | `HeartbeatJob` ([§6](#quartz)) |

- ***`startup` exists only when started through `main`.*** `@SpringBootTest`
  doesn't call `main`, so the test context has no `BufferingApplicationStartup`
  and no `startup` endpoint.
- ***`sbom` needs a Maven build.*** The CycloneDX file is generated during the
  package lifecycle. Run `mvn package` (or `verify`) once if the view is empty.
- ***Why there's no `jolokia`***: the SBA 4 server is WebFlux only and
  `jolokia-support-springboot` needs a servlet container, so the SBA "JMX" view
  isn't available for admin-server. The servlet-based services can still offer it.
- ***`heapdump` is sensitive***: a heap dump contains secrets in memory (including
  the passwords above). Keep the port private, or drop it from the exposure list.

<a id="quartz"></a>
## <span style="color:hsl(13,80%,58%)">6. ⏱️ Quartz demo job</span>

- [QuartzConfig.java](src/main/java/com/learning/discovery/admin/job/QuartzConfig.java)
  declares a durable `JobDetail` `demo.heartbeatJob` and a `SimpleTrigger`
  `demo.heartbeatTrigger` that fires ***every minute, forever***.
- [HeartbeatJob.java](src/main/java/com/learning/discovery/admin/job/HeartbeatJob.java)
  extends `QuartzJobBean` and logs `Heartbeat from admin-server (fire time ...)`.
- It exists so the SBA ***Quartz*** view has something to show: jobs, triggers,
  next fire time. The scheduler is Boot's in-memory `RAMJobStore` (no JDBC store
  is configured).

```java
@Bean
Trigger heartbeatTrigger(JobDetail heartbeatJobDetail) {
    return TriggerBuilder.newTrigger()
            .forJob(heartbeatJobDetail)
            .withIdentity("heartbeatTrigger", "demo")
            .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever())
            .build();
}
```

<a id="logging"></a>
## <span style="color:hsl(229,80%,58%)">7. 📝 Logging to file</span>

- `logging.file.name: logs/${spring.application.name}.log` → `logs/admin-server.log`,
  relative to the working directory. Running from the module directory puts it
  at `admin-server/logs/`; running from the repo root puts it at `logs/`.
- ***Setting a log file is what turns on `/actuator/logfile`***, and with it the
  SBA "Logfile" view. Without a file the endpoint returns 404.
- The heartbeat line ([§6](#quartz)) appears there once a minute, which is a
  quick way to check the Logfile view works.
- [banner.txt](src/main/resources/banner.txt) prints an "ADMIN SVC" ASCII banner at startup.

<a id="configuration"></a>
## <span style="color:hsl(271,80%,58%)">8. ⚙️ Configuration reference</span>

[application.yml](src/main/resources/application.yml), annotated:

<details><summary><b>application.yml — click to view</b></summary>

```yaml
spring:
  application:
    name: admin-server                     # Eureka app name ADMIN-SERVER; log file name
  security:
    user:                                  # one user for the UI form login AND actuator Basic auth
      name: ${ACTUATOR_USER:admin}
      password: ${ACTUATOR_PASSWORD:admin}
      roles: ACTUATOR
  boot:
    admin:
      ui:
        remember-me-enabled: false         # no reactive rememberMe(); hide the checkbox
      instance-auth:                       # Basic auth SBA sends to each instance's actuator
        enabled: true                      # metadata user.name/user.password wins,
        default-user-name: ${spring.security.user.name}   # these are the fallback
        default-password: ${spring.security.user.password}

server:
  port: 8084

eureka:
  client:
    service-url:                           # the registry itself is Basic-protected
      defaultZone: http://${spring.security.user.name}:${spring.security.user.password}@localhost:8761/eureka/
  instance:
    prefer-ip-address: true                # register by IP, not hostname
    metadata-map:                          # SBA discovers itself, so it needs its own credentials
      user.name: ${spring.security.user.name}
      user.password: ${spring.security.user.password}

logging:
  file:
    name: logs/${spring.application.name}.log   # enables /actuator/logfile

management:
  endpoints:
    web:
      exposure:
        include: [health, info, heapdump, logfile, env, shutdown, restart, refresh,
                  metrics, loggers, threaddump, configprops, beans, mappings,
                  httpexchanges, startup, auditevents, sbom, quartz]
  endpoint:
    shutdown: { access: unrestricted }
    restart:  { access: unrestricted }
    refresh:  { access: unrestricted }
    env:      { post: { enabled: true } }
    health:   { show-details: always }
    heapdump: { access: unrestricted }
```

</details>

| Environment variable | Default | Used for |
|---|---|---|
| `ACTUATOR_USER` | `admin` | UI login, actuator Basic, Eureka URL, metadata, instance-auth default |
| `ACTUATOR_PASSWORD` | `admin` | same as above |

<a id="tests"></a>
## <span style="color:hsl(311,80%,58%)">9. 🧪 Tests</span>

[AdminServerApplicationIT.java](src/test/java/com/learning/discovery/admin/AdminServerApplicationIT.java):
`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureWebTestClient`
(WebFlux) + `@ActiveProfiles("test")`.
[application-test.yml](src/test/resources/application-test.yml) sets
`eureka.client.enabled: false`, so no live registry is needed.

| Test | What it checks |
|---|---|
| `healthEndpointReportsUpWithoutCredentials` | `GET /actuator/health` without credentials → `200`, `status=UP` (permitAll) |
| `uiRootWithoutLoginRedirectsToLoginPage` | `GET /` with `Accept: text/html` → `302` to `/login` (form login entry point) |
| `apiWithoutCredentialsIsUnauthorized` | `GET /applications` with `Accept: application/json` → `401` (Basic entry point) |
| `loginPageIsPublicAndCarriesCsrfToken` | `GET /login` → `200`, an `XSRF-TOKEN` cookie is set, and the HTML contains `"parameterName":"_csrf"` (proves `csrfCookieWebFilter` + `CsrfControllerAdvice`) |
| `formLoginWithCsrfTokenRedirectsToUiAndIsAudited` | POST `/login` with cookie + `_csrf` → `302` to `/`; then `/actuator/auditevents?type=AUTHENTICATION_SUCCESS` shows principal `admin` (proves the auditing wrapper) |
| `formLoginWithoutCsrfTokenIsForbidden` | POST `/login` with no token → `403` |
| `quartzHeartbeatJobIsRegistered` | `GET /actuator/quartz/jobs/demo/heartbeatJob` → `200`, `name=heartbeatJob` |

Run:

```bash
# this module only (failsafe runs *IT classes in the verify phase)
mvn -pl admin-server verify

# single test
mvn -pl admin-server verify -Dit.test=AdminServerApplicationIT
```

<a id="gotchas"></a>
## <span style="color:hsl(46,80%,58%)">10. 🛠️ Gotchas and troubleshooting</span>

| Symptom | Likely cause | Fix |
|---|---|---|
| ***Dashboard is empty*** | eureka-server not running or not reachable, or wrong credentials in `defaultZone` | start `eureka-server` first; check `ACTUATOR_USER`/`ACTUATOR_PASSWORD` match; wait ~30s for the registry fetch |
| ***Instance shows `OFFLINE`*** | SBA can't reach the registered address, or gets a 401 from its actuator | check the service sets `prefer-ip-address: true` (hostname may not resolve) and has `metadata-map.user.name/user.password`; credentials must match that service's `spring.security.user` |
| Instance `UP` but many views missing | endpoint not exposed on that service | add it to that service's `management.endpoints.web.exposure.include` |
| ***Login returns 403*** | CSRF token missing: the cookie is blocked, or `CsrfControllerAdvice` was removed | allow cookies for `localhost:8084`; keep the advice and `csrfCookieWebFilter` |
| Login page loops back to `/login` | wrong password (check the Audit Log for `AUTHENTICATION_FAILURE`) | use the env-var credentials |
| curl to the API gets `401` instead of a redirect | expected: non-HTML requests get the Basic challenge | `curl -u admin:admin http://localhost:8084/applications` |
| Logfile view says not found | no `logging.file.name`, or the file was deleted | keep the property; the file is recreated on the next log line |
| SBOM view empty | app started without a Maven build that ran CycloneDX | `mvn -pl admin-server package` once |
| Restarted eureka-server, instances vanish | registry is in-memory; clients re-register on their next heartbeat (~30s) | ***restart order: eureka-server first***, then the services, then admin-server; or just wait |
| "Restart" on admin-server itself drops the UI session | the context restarts, and so does the in-memory session store | log in again |

<a id="qa"></a>
## <span style="color:hsl(0,80%,58%)">11. ❓ Q&A</span>

Questions go here.
