# learning-service-discovery

Eureka service discovery demo. 4 Maven modules: 1 Eureka registry + 3 REST
microservices that register themselves with it.

## Modules

| Module            | Port | Role                          | REST API                          | Swagger UI                              |
|-------------------|------|--------------------------------|------------------------------------|------------------------------------------|
| `eureka-server`   | 8761 | Service registry / dashboard  | —                                   | —                                        |
| `user-service`    | 8081 | Eureka client                 | `GET /api/users`, `GET /api/users/{id}`       | http://localhost:8081/swagger-ui.html    |
| `order-service`   | 8082 | Eureka client                 | `GET /api/orders`, `GET /api/orders/{id}`     | http://localhost:8082/swagger-ui.html    |
| `product-service` | 8083 | Eureka client                 | `GET /api/products`, `GET /api/products/{id}` | http://localhost:8083/swagger-ui.html    |

## Parent / BOM chain

```
super-pom (com.org.llm:super-pom:1.0.0)   ← corporate parent, imports learning-bom
  └── learning-service-discovery (com.learning:learning-service-discovery)  ← this repo's root pom, packaging=pom
        ├── eureka-server
        ├── user-service
        ├── order-service
        └── product-service
```

- Root `pom.xml` declares `super-pom` as `<parent>`. `super-pom` imports
  `learning-bom` in its `dependencyManagement`.
- `learning-bom` already imports `spring-boot-dependencies`,
  `spring-cloud-dependencies` (Eureka artifacts) and the `springdoc` starters —
  every dependency this repo uses is already version-managed there.
- **No `<version>` is declared anywhere in this project** — every module pom
  and every `<dependency>` relies entirely on the inherited
  `dependencyManagement` chain (super-pom → learning-bom). Module artifact
  versions (`0.0.1-SNAPSHOT`) come from the parent `<parent><version>` +
  inheritance, never hardcoded per-module.
- No new dependency versions needed adding to `learning-bom` — Eureka
  (`spring-cloud-starter-netflix-eureka-server` / `-client`) and Swagger
  (`springdoc-openapi-starter-webmvc-ui`) were already present in its
  dependency management before this repo was created.

## Run order

Eureka server must be up first so the 3 clients have somewhere to register.

```bash
cd learning-service-discovery
mvn -pl eureka-server spring-boot:run &
# wait for it to come up, then:
mvn -pl user-service spring-boot:run &
mvn -pl order-service spring-boot:run &
mvn -pl product-service spring-boot:run &
```

Or build everything first: `mvn clean install`, then run each module's jar.

## Verify

- **Eureka dashboard**: http://localhost:8761 — lists `USER-SERVICE`,
  `ORDER-SERVICE`, `PRODUCT-SERVICE` once all 3 clients register (takes a few
  seconds after startup; default renewal interval).
- **Health checks** (Spring Boot Actuator, `show-details: always`):
  - http://localhost:8761/actuator/health
  - http://localhost:8081/actuator/health
  - http://localhost:8082/actuator/health
  - http://localhost:8083/actuator/health
- **Swagger UI / OpenAPI** for each microservice (springdoc default paths):
  - UI: `http://localhost:<port>/swagger-ui.html`
  - Raw spec: `http://localhost:<port>/v3/api-docs`
- **Sample REST calls**:
  ```bash
  curl http://localhost:8081/api/users
  curl http://localhost:8082/api/orders
  curl http://localhost:8083/api/products
  ```

## Tests

Each microservice module has:
- **Unit tests** (`*ControllerTest`) — `@WebMvcTest` + `MockMvc`, no Spring
  context beyond the web layer, no network/Eureka involved.
- **Integration tests** (`*ApplicationIT`) — `@SpringBootTest` with a random
  port + `TestRestTemplate`, hitting the real embedded servlet container and
  actuator health endpoint. `eureka.client.enabled=false` in
  `src/test/resources/application-test.yml` so tests don't need a live Eureka
  server.

`eureka-server` has its own `@SpringBootTest` that boots the registry and
asserts the dashboard and health endpoint respond.

Run all tests: `mvn clean verify` (failsafe plugin from `super-pom` runs the
`*IT` classes in the `integration-test` phase; surefire runs `*Test` in the
`test` phase).
