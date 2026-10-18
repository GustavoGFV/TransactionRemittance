# Inter Remittance – Cross Border

REST API for international remittances (BRL → USD and back), built with Java 21, Spring Boot 4, PostgreSQL and Maven.

## Architecture

| Package | Responsibility |
|---|---|
| `user` | User CRUD (PF/PJ, CPF/CNPJ validation, BRL/USD balances) |
| `quotation` | BCB PTAX client (OpenFeign + circuit breaker + retries), weekend/holiday fallback, Caffeine cache |
| `remittance` | Core business rules: balances, preview, buy, reverse, daily limits. Orchestrates `user` and `quotation` |
| `security` | JWT (HS256) issuing and resource-server validation |

```mermaid
flowchart LR
    Client([Client]) -->|"POST /auth/token"| Auth[AuthController]
    Auth -->|"JWT HS256"| Client
    Client -->|"Bearer JWT"| Sec{{"Spring Security<br/>JWT resource server"}}

    subgraph App["Spring Boot (Java 21, virtual threads)"]
        Sec --> RC[RemittanceController]
        Sec --> UC[UserController]
        RC --> RS["RemittanceService<br/>orchestration, CompletableFuture"]
        RS -->|"1. current rate (outside the transaction)"| QS[QuotationService]
        RS -->|"2. buy / reverse"| RTS["RemittanceTransactionService<br/>@Transactional"]
        UC --> US[UserService]
        RTS --> UR[UserRepository]
        RTS --> LR["DailyLimitRule / UserDailyTransaction<br/>repositories"]
        US --> UR
        QS --> Cache[("Caffeine cache<br/>TTL by date")]
        Cache -.->|miss| Feign["BcbPtaxClient (OpenFeign)<br/>Resilience4j circuit breaker + 3 retries"]
    end

    Feign -->|"cotacaoCompra"| BCB[("BCB PTAX API")]
    UR --> DB[("PostgreSQL")]
    LR --> DB
    DB --- Locks["ACID, SELECT ... FOR UPDATE (ordered by id),<br/>@Version, UNIQUE(user, date)"]
```

Key decisions:
- **IDs:** internal `BIGINT` identity primary key; a `public_id` UUID is what the API exposes as `id`.
- **Daily limit:** `daily_limit_rules` (CPF R$ 10,000 / CNPJ R$ 50,000) and `user_daily_transactions` (running total per user per day). Every buy (same-account or external) counts; PF and PJ can send to each other within the limits. Reverse (USD → BRL) is not counted.
- **ACID:** each remittance is one `@Transactional` unit. Rows are locked ordered by id (no deadlocks); the quotation is fetched *before* the transaction so no connection is held while waiting for the BCB.
- **Cache:** quotations are cached by date (Caffeine, TTL `app.cache.quotations.ttl`, default 10 min). Failures are never cached.
- **Concurrency:** virtual threads + `CompletableFuture`; Hikari pool is fixed-size (20) with short timeouts.
- **Errors:** RFC 7807 `ProblemDetail` responses (400, 401, 404, 409, 422, 503).

## Improvements and rationale

Beyond the mandatory requirements, the following decisions were made on purpose. Each one lists the reason.

| Area | Improvement | Reason |
|---|---|---|
| Database | PostgreSQL instead of an in-memory database, with Flyway migrations | Remittances need real row locking (`SELECT ... FOR UPDATE`), `@Version` and constraints that behave like production. An in-memory database (H2) would hide concurrency issues. Flyway makes the schema versioned and reproducible |
| Data model | Internal `BIGINT` PK + public `UUID` exposed by the API | Compact, fast joins and indexes internally, while sequential ids are never leaked to clients (no enumeration) |
| Data model | `daily_limit_rules` and `user_daily_transactions` tables, `UNIQUE(user, date)` | Limits are data, not code: changing a cap needs no deploy. The per-day running total is O(1) to check and doubles as an audit trail |
| Consistency | One `@Transactional` unit per remittance, pessimistic locks ordered by id, `@Version` | Any failure rolls back balances and the daily counter. Ordered locking avoids deadlocks; the version column is a second guard against lost updates |
| Consistency | Quotation fetched **before** opening the transaction | No database connection or lock is held while waiting for a network call |
| Resilience | OpenFeign + Resilience4j circuit breaker + 3 retries | Declarative HTTP client; the breaker stops cascading failures when the BCB is down and the API answers a clean `503` |
| Resilience | Weekend/holiday fallback to the last business day | The BCB publishes no bulletin on non-business days; the last available `cotacaoCompra` is used, with a bounded number of attempts |
| Performance | Caffeine cache by date (TTL 10 min, failures never cached) | The rate changes a few times a day; avoids hammering the BCB and cuts latency under high request volume |
| Performance | Virtual threads + `CompletableFuture` | I/O-bound work (BCB calls) does not block platform threads, so throughput scales without a large thread pool |
| Performance | Fixed-size HikariCP pool with short timeouts; G1GC, percentage-based heap, `AlwaysPreTouch` | Predictable memory and connections inside containers, avoiding pauses and latency spikes |
| Security | JWT (HS256) resource server on `/users/**` and `/remittances/**`; BCrypt password hashes | Sensitive endpoints require a Bearer token; passwords are never stored or returned in clear text and are excluded from `toString` |
| Errors | RFC 7807 `ProblemDetail` with a global `@RestControllerAdvice` | Consistent, machine-readable error contract (400, 401, 404, 409, 422, 503) |
| Design | Layered packages (`controller`, `service`, `repository`, `dto`, `mapper`), records + MapStruct, constructor injection | Low coupling, SOLID, immutable DTOs, easy to mock and test |
| Documentation | OpenAPI/Swagger with descriptions, examples and required flags on every endpoint and field | Self-explanatory API for evaluators and consumers |
| Quality | Unit tests (JUnit 5 + Mockito) and integration tests (Testcontainers + PostgreSQL), JaCoCo coverage | Business rules are verified in isolation and end to end against the real database |
| Quality | SonarQube quality gate in the Docker build | A container image is only produced if code quality, coverage and vulnerability checks pass |
| Operations | Multi-stage `Dockerfile`, `docker-compose`, Kubernetes manifests (probes, resources, secrets), Actuator health | Same artifact from laptop to cluster; the app is observable and restarts safely |
## Build and run

Requirements: JDK 21, Docker.

```bash
# PostgreSQL only, then the app from Maven
docker compose up -d postgres
./mvnw spring-boot:run

# Everything in containers (app + PostgreSQL)
docker compose --profile app up --build -d
```

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

### Authentication

```bash
curl -X POST http://localhost:8080/auth/token -H "Content-Type: application/json" \
  -d '{"clientId":"remittance-client","clientSecret":"remittance-secret"}'
```

Send the returned token as `Authorization: Bearer <token>` (Swagger: **Authorize**). `/users/**` and `/remittances/**` are secured; `/quotations` is public. Override the dev defaults with `JWT_SECRET` (≥ 32 bytes), `JWT_TTL`, `AUTH_CLIENT_ID`, `AUTH_CLIENT_SECRET`.

## Step-by-step test guide

Run everything from Swagger UI (**Authorize** with the token) or with `curl` (Bash; on Windows use `curl.exe` and Git Bash/WSL, or paste the JSON in Swagger).

```bash
# 0. Start the stack and get a token
docker compose --profile app up --build -d
TOKEN=$(curl -s -X POST localhost:8080/auth/token -H "Content-Type: application/json" \
  -d '{"clientId":"remittance-client","clientSecret":"remittance-secret"}' | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"

# 1. Quotation (public). Date format is 'MM-dd-yyyy' with single quotes; weekends return the last business day
curl -s -g "localhost:8080/quotations?date='10-01-2026'"

# 2. Create a sender (PF, R$ 1,500) and a recipient (PJ). Valid CPF/CNPJ, unique e-mail and document
curl -s -X POST localhost:8080/users -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Maria Silva","email":"maria@mail.com","document":"529.982.247-25","password":"S3nh@Forte2026","balanceBrl":1500.00}'
curl -s -X POST localhost:8080/users -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Empresa LTDA","email":"empresa@mail.com","document":"11.222.333/0001-81","password":"S3nh@Forte2026"}'
# Keep the returned "id" values: SENDER_ID and RECIPIENT_ID

# 3. Check balances and preview the BRL cost of USD 100
curl -s -H "$AUTH" localhost:8080/remittances/users/$SENDER_ID/balance
curl -s -H "$AUTH" "localhost:8080/remittances/users/$SENDER_ID/preview?usdAmount=100.00"

# 4. Buy USD for another user (external transfer: recipientDocument is required)
curl -s -X POST localhost:8080/remittances/buy -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"userId":"'$SENDER_ID'","amountBrl":500.00,"isSameAccount":false,"recipientDocument":"11222333000181"}'

# 5. Buy USD into the sender's own USD wallet (no recipient)
curl -s -X POST localhost:8080/remittances/buy -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"userId":"'$SENDER_ID'","amountBrl":100.00,"isSameAccount":true}'

# 6. Reverse USD back to BRL (recipient must hold USD; use its id)
curl -s -X POST localhost:8080/remittances/reverse -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"userId":"'$RECIPIENT_ID'","amountUsd":10.00}'
```

Expected error scenarios (RFC 7807 bodies):

| Scenario | How to trigger | Result |
|---|---|---|
| No / invalid token | Omit the `Authorization` header on `/users` or `/remittances` | `401` |
| Invalid or duplicated CPF/CNPJ or e-mail | Repeat step 2, or use `111.111.111-11` | `400` / `409` |
| Weak password | `"password":"123"` in step 2 | `400` |
| Insufficient balance | `amountBrl` above the sender's BRL balance | `422`, balances unchanged |
| Daily limit exceeded | Buys totalling more than R$ 10,000 (PF) or R$ 50,000 (PJ) in one day | `422`, nothing is debited |
| Minimum amount | `amountBrl` below `1.00` | `400` |
| Invalid date | `/quotations?date=2026-10-01` | `400` |
| BCB unavailable | Stop network access to the BCB | `503`, circuit breaker opens |

After each remittance, call the balance endpoint again: BRL decreases, USD increases, and a failed request leaves both untouched (ACID rollback).
## Tests

```bash
./mvnw test      # unit tests (no Docker needed)
./mvnw verify    # unit + integration tests; integration tests start PostgreSQL with Testcontainers (Docker required)
```

`RemittanceFlowIT` exercises the full HTTP flow against a real PostgreSQL: token, document validation and duplicates, daily limit, rollback on failure, quotation cache and BCB outage (only the BCB client is mocked). The JaCoCo report is generated at `target/site/jacoco/index.html`.

## SonarQube quality gate

```bash
docker compose --profile quality up -d sonarqube      # http://localhost:9000 (admin/admin), create a token
export SONAR_TOKEN=<token>
docker build --build-arg SONAR_HOST_URL=http://host.docker.internal:9000 \
  --secret id=sonar_token,env=SONAR_TOKEN -t remittance .
```

The Dockerfile has a `quality` stage between build and runtime: the image is only produced if the quality gate passes. Without `SONAR_HOST_URL` the analysis is skipped. Unit tests and coverage run in the build stage; integration tests run in CI (`./mvnw verify`) because they need a Docker daemon.

## Kubernetes

```bash
kubectl apply -f k8s/secret.yaml -f k8s/database -f k8s/app
```

The deployment sets G1GC, heap as a percentage of the container limit (memory request = limit), Hikari pool size and probes. Replace the secrets in `k8s/secret.yaml` before using it for real.

## Notes

- The BCB endpoint used is `CotacaoDolarDia`; on weekends the last business day is used (Saturday → Friday, Sunday → Friday, holidays by bounded retry on previous days).
- Deviations from the original statement: PostgreSQL is used instead of an in-memory database (Flyway + Testcontainers keep it reproducible), and user passwords are stored as BCrypt hashes (API access is still protected by client-credentials JWT; per-user login is not part of the scope).
- Configuration is externalised through environment variables (see `application.properties`).
