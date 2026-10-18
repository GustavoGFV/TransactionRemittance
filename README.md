# Inter Remittance – Cross Border

REST API for international remittances (BRL → USD and back), built with Java 21, Spring Boot 4, PostgreSQL and Maven.

## Architecture

| Package | Responsibility |
|---|---|
| `user` | User CRUD (PF/PJ, CPF/CNPJ validation, BRL/USD balances) |
| `quotation` | BCB PTAX client (OpenFeign + circuit breaker + retries), weekend/holiday fallback, Caffeine cache |
| `remittance` | Core business rules: balances, preview, buy, reverse, daily limits. Orchestrates `user` and `quotation` |
| `security` | JWT (HS256) issuing and resource-server validation |

Key decisions:
- **IDs:** internal `BIGINT` identity primary key; a `public_id` UUID is what the API exposes as `id`.
- **Daily limit:** `daily_limit_rules` (CPF R$ 10,000 / CNPJ R$ 50,000) and `user_daily_transactions` (running total per user per day). Only external transfers count.
- **ACID:** each remittance is one `@Transactional` unit. Rows are locked ordered by id (no deadlocks); the quotation is fetched *before* the transaction so no connection is held while waiting for the BCB.
- **Cache:** quotations are cached by date (Caffeine, TTL `app.cache.quotations.ttl`, default 10 min). Failures are never cached.
- **Concurrency:** virtual threads + `CompletableFuture`; Hikari pool is fixed-size (20) with short timeouts.
- **Errors:** RFC 7807 `ProblemDetail` responses (400, 401, 404, 409, 422, 503).

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

- The BCB endpoint used is `CotacaoDolarDia`; on weekends the last business day is used.
- Configuration is externalised through environment variables (see `application.properties`).
