# Order Processing & Notification System

A simplified order-processing Spring Boot service demonstrating REST APIs, controlled lifecycle, multi-channel notifications, JWT security, and resilient error handling.

## Quickstart

```bash
# Start the WireMock notification stub (email + sms)
docker compose up -d

# Run the app
./gradlew bootRun
```

App on `http://localhost:8080`. Swagger UI at `http://localhost:8080/swagger-ui.html`. WireMock on `http://localhost:9561`.

```bash
# 1. Login
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-pass"}' | jq -r .accessToken)

# 2. Create order
curl -X POST localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"c-1","items":[{"productId":"p-1","quantity":2,"unitPrice":9.99}]}'

# 3. Update status (admin only)
curl -X PATCH localhost:8080/api/v1/orders/{id}/status \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"COMPLETED"}'

# 4. Search
curl "localhost:8080/api/v1/orders?status=CREATED&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

The search endpoint supports the following query params (all optional, combinable):

| Param | Type | Example |
|---|---|---|
| `status` | `CREATED` \| `COMPLETED` \| `CANCELLED` | `status=COMPLETED` |
| `customerId` | string | `customerId=c-1` |
| `createdFrom` | ISO-8601 instant (inclusive) | `createdFrom=2026-05-01T00:00:00Z` |
| `createdTo` | ISO-8601 instant (inclusive) | `createdTo=2026-05-10T23:59:59Z` |
| `page`, `size` | int | `page=0&size=20` |
| `sort` | `field,asc\|desc` (repeatable) | `sort=createdAt,desc` |

```bash
# Filter by customer
curl "localhost:8080/api/v1/orders?customerId=c-1" \
  -H "Authorization: Bearer $TOKEN"

# Date-range window
curl "localhost:8080/api/v1/orders?createdFrom=2026-05-01T00:00:00Z&createdTo=2026-05-10T23:59:59Z" \
  -H "Authorization: Bearer $TOKEN"

# Combine filters: completed orders for one customer in May, newest first
curl "localhost:8080/api/v1/orders?status=COMPLETED&customerId=c-1\
&createdFrom=2026-05-01T00:00:00Z&createdTo=2026-05-31T23:59:59Z\
&page=0&size=20&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"

# Sort by status ascending then createdAt descending
curl "localhost:8080/api/v1/orders?sort=status,asc&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"
```

A ready-to-use `requests.http` (IntelliJ HTTP Client format, with `http-client.env.json`) is in the project root.

Run tests + coverage:

```bash
./gradlew clean build
# JaCoCo report: build/reports/jacoco/test/html/index.html
```

Run only the integration tests:

```bash
./gradlew test --tests "com.example.orders.integration.*"
```

---

## Project structure

```
src/main/java/com/example/orders/
  OrderApplication.java
  config/
    OpenApiConfig.java
  common/
    error/                # GlobalExceptionHandler, domain exceptions, error codes
    logging/              # TraceIdFilter (request-scoped MDC)
  order/
    api/                  # OrderController
    dto/                  # CreateOrderRequest, UpdateStatusRequest,
                          # OrderResponse, OrderWithNotificationsResponse
    entity/               # Order, OrderItem, OrderStatus (JPA entities)
    repository/           # OrderRepository, OrderSpecifications
    service/              # OrderService, StateTransitionValidator, OrderResult
  notification/
    api/                  # NotificationService (sync, REST, @Retryable)
    dto/                  # NotificationOutcome
    config/               # NotificationProperties, NotificationClientConfig
  security/
    AuthController        # demo issuer of JWTs
    JwtService            # signs/parses tokens (HS256)
    SecurityConfig        # filter chain, JWT decoder
    AppSecurityProperties # binds app.security.jwt
    dto/                  # AuthRequest, AuthResponse

src/main/resources/
  application.yml
  db/
    migration/V1__init.sql               # schema
    seed/V2__seed_orders.sql             # demo data (60 orders, mixed)

wiremock/
  mappings/{email,sms}.json              # stub responses for notification calls

docker-compose.yml                       # wiremock service
requests.http + http-client.env.json     # IntelliJ HTTP Client tests
src/test/...                             # mirrors main; integration test uses embedded WireMock
```

---

## Key technical decisions

This section explains **why** each technical choice was made. Sections follow the order of `requirements.md`.

### Stack — Spring Boot 3.5+, Java 21, Gradle

- **Spring Boot 3.5+**: latest stable line, Jakarta EE 10, native `ProblemDetail` support.
- **Java 21 LTS**: long-term-supported runtime; access to virtual threads if a future iteration needs them.
- **Gradle**: more flexible build than Maven; convenient for incremental compilation and JaCoCo wiring.

### 1. Order API Management

Four endpoints under `/api/v1`, exactly mirroring the requirement:

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/orders` | Create |
| GET | `/api/v1/orders/{id}` | Retrieve |
| PATCH | `/api/v1/orders/{id}/status` | Update status (lifecycle-validated, ADMIN only) |
| GET | `/api/v1/orders` | Search (filter + paginate) |

- **Dedicated `PATCH /{id}/status`** rather than a generic `PUT /{id}`: the controlled lifecycle is the most important business rule, so it gets its own endpoint with explicit validation. Order line items are immutable post-creation — no general "update order" endpoint is exposed.
- **Search via `GET` query params**, not `POST /search`: RESTful, cacheable, easy to demo with curl. Filters compose via JPA `Specification` (`OrderSpecifications`), so adding new filter fields is one method.
- **DTOs separated from entities** (`order/dto/` vs `order/entity/`) so wire shape and persistence shape evolve independently. Controllers never expose JPA entities directly.

#### Status lifecycle — explicit state machine

```
CREATED ──► CANCELLED  (terminal)
CREATED ──► COMPLETED  (terminal)
```

CANCELLED and COMPLETED are terminal — no further transitions. Same-state and terminal-from transitions return **409 Conflict** with error code `ORDER_INVALID_STATE_TRANSITION`. The `StateTransitionValidator` lives in the service layer (not the controller) so the rule is enforced regardless of how the order is mutated.

### 2. Notifications

This is the largest design decision. The brief asks for multi-channel notifications on create / status-change. Choices made:

- **Synchronous, in-service.** `OrderService` calls `NotificationService` directly inside `create` / `updateStatus` and the controller surfaces the per-channel outcomes back to the client. No events, no listeners, no async dispatcher. The earlier iteration used `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`; that indirection bought nothing for this scope and made the success-and-also-partial-failure response harder to assemble. The straight-line call is simpler and lets the controller honestly report what happened.
- **Retry via Spring Retry.** Each channel call is annotated `@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200, multiplier = 2))`. After 3 attempts, `@Recover` records a `FAILED` `NotificationOutcome` with the captured error. Because `@Retryable` only fires through Spring's proxy, `NotificationService` self-injects via `@Lazy` so the per-channel send goes through the proxy.
- **REST via `RestClient`.** Synchronous Spring 6 client, no WebFlux pulled in for this. WireMock acts as the external system both in `bootRun` (via Docker) and in integration tests (embedded).
- **Configuration.** A single base URL plus per-channel toggles:
  ```yaml
  notifications:
    url: http://localhost:9561/
    channels:
      email: { enabled: true }
      sms:   { enabled: true }
  ```
  The service composes `${url}/${channelName}` per call. Add a channel by enabling it in yml — no Java change required to add another endpoint at the same host.
- **Partial-success response — HTTP 207 Multi-Status.** When the order saves but one or more channels exhaust retries, the controller responds **207** with a body listing per-channel results. This honestly reflects reality (the order is committed; some side effect failed) instead of either lying with a 2xx or implying the order doesn't exist with a 5xx.

```json
{
  "order": { "id": "...", "status": "CREATED", ... },
  "notifications": [
    { "channel": "email", "status": "SENT" },
    { "channel": "sms",   "status": "FAILED", "errorCode": "NOTIF_SMS_FAILED", "message": "..." }
  ]
}
```

### 3. Persistence

- **H2 in-memory** keeps the demo zero-setup while running in Postgres-compat mode so the SQL stays portable to a real DB.
- **Flyway** for versioned migrations (`db/migration/V1__init.sql`) instead of `ddl-auto`. Schema is checked in, reviewable, and reproducible. Auto-generated DDL hides what's actually in the DB and is unsafe in production.
- **Schema vs seed split.** Two Flyway locations: `db/migration` (schema only) and `db/seed` (sample orders). Tests load only `db/migration` so they start from an empty DB; `bootRun` loads both and gets a populated dataset for paging/filter demos (60 orders across 10 customers and all three statuses).
- **JPA Auditing** (`@CreatedDate`, `@LastModifiedDate`) so timestamps are managed by the framework, never by hand.
- **`@Version` optimistic lock** on `Order`. Status updates are precisely the place where two requests can race (e.g., a CANCEL and a COMPLETE arriving concurrently); the version column makes the second one fail fast with `OptimisticLockingFailureException` rather than silently overwriting.
- **Indexes on `status`, `customer_id`, `created_at`** to keep the search endpoint sensible as data grows.

### 4. Error Handling

- **Format:** `application/problem+json` per RFC 7807, using Spring Boot 3's native `ProblemDetail`. Extra members: `errorCode`, `timestamp`, `traceId`. A documented standard rather than a hand-rolled envelope.
- **Centralized in `GlobalExceptionHandler`** (`@RestControllerAdvice` extending `ResponseEntityExceptionHandler`). Domain exceptions (`OrderNotFoundException`, `InvalidStateTransitionException`) map to specific HTTP statuses with stable error codes.
- **Logging:** SLF4J + Logback. A `TraceIdFilter` populates MDC with a request-scoped UUID so every log line within one request can be correlated. Levels: ERROR for unexpected/5xx, WARN for client-induced 4xx, INFO for lifecycle transitions and notification outcomes.
- **Retry** is implemented via Spring Retry on the per-channel notification call (see §2). It is **not** applied at the controller level — retrying user-driven calls would risk duplicate orders. Idempotency belongs to the client (or to outbox + consumer) and is intentionally out of scope.

### 5. Security

- **Spring Security `oauth2-resource-server`** with a `NimbusJwtDecoder` validating HMAC-SHA256 signed tokens. No external IdP — the symmetric key sits in `app.security.jwt.secret` (with a placeholder default in `application.yml`).
- **Demo issuer endpoint** `POST /api/v1/auth/login`: logs the username and returns a 1-hour JWT. There is **no password check and no user store** — this is intentional, demo-grade, and explicit. Roles are derived from the username (`admin` → `[USER, ADMIN]`, anything else → `[USER]`) so the role-based authorization downstream is exercisable. In a real system the issuer would be replaced with a proper IdP; nothing in the resource-server side would change.
- **Stateless filter chain** (`SessionCreationPolicy.STATELESS`). No `JSESSIONID` is issued; every request re-validates the bearer token. Pairs cleanly with disabled CSRF (CSRF only matters when there's session-bound cookie auth).
- **Role-based authorization.** `ROLE_USER` (read/create/search) and `ROLE_ADMIN` (additionally can `PATCH /orders/*/status`). Wired via `requestMatchers(...).hasRole(...)` in the security chain.
- Why JWT and not HTTP Basic? It's the more realistic choice for a stateless REST API and cleanly separates authentication (login) from authorization (per-request token validation), a more honest demo of how Spring Security is used in practice.

### 6. Testing

- **Unit (JUnit 5 + Mockito):** state-machine rules in `OrderService`, `JwtService` sign/parse, `NotificationService` retry/recover behavior.
- **Slice:** `@WebMvcTest` for controller validation, error mapping, and auth rules.
- **Integration:** `@SpringBootTest` + embedded WireMock — full request through DB through stubbed channels. WireMock scenarios verify the retry-then-recover path and the 207 partial-success response. JWT login → protected-endpoint flow is end-to-end tested.
- **Why WireMock and not `@MockBean NotificationService`?** Mocking our own bean would skip exactly the parts worth testing: URL composition, payload shape, status-code-to-outcome mapping, and the `@Retryable` wiring. Mock at the system boundary, not at our own service.
- **JaCoCo** report at `build/reports/jacoco/test/html/index.html`. Target ~80% on service+controller layers — coverage for signal, not vanity.

### 7. Documentation

- **This README** explains the *why*.
- **`docs/PRD.md`** captures product scope, user stories, acceptance criteria, error catalog, and non-functional requirements.
- **OpenAPI** spec is auto-generated by `springdoc-openapi` and served at `/swagger-ui.html` — living API documentation.
- **`requests.http`** + `http-client.env.json` give a runnable, version-controlled smoke test for every endpoint via the IntelliJ HTTP Client.

---

## Default credentials

| Username | Password (ignored) | Role |
|---|---|---|
| `admin` | any | `ROLE_USER`, `ROLE_ADMIN` |
| anything else | any | `ROLE_USER` |

The auth endpoint is a demo issuer — it does not validate the password. See §5.
