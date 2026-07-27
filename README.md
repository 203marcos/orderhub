# OrderHub — Distributed Order Processing Platform

A portfolio project showing how to build a small commerce domain as **event-driven Java microservices** — with synchronous and asynchronous communication, resilience, and full observability, kept deliberately focused rather than over-engineered.

> 📐 Full design rationale, diagrams, and a new-developer guide: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**

## Architecture

```mermaid
flowchart TB
    client([Client]) --> gw["api-gateway :8080<br/>JWT validation + routing"]
    gw --> auth[auth-service :8081]
    gw --> catalog[catalog-service :8082]
    gw --> order[order-service :8083]
    gw --> payment[payment-service :8084]

    order -- "sync REST (Feign, circuit breaker)" --> catalog
    order -- "outbox relay" --> kafka{{"Kafka (KRaft)"}}
    kafka -- "order.created" --> payment
    payment -- "outbox relay" --> kafka
    kafka -- "payment.*" --> order
    kafka -- "payment.approved" --> notif[notification-service :8085]
    notif --> mail[Mailhog]
```

Only the gateway binds a host port. The services trust the identity headers the gateway
derives from the JWT, so publishing them on localhost would let anyone forge those headers.

**Order lifecycle (choreography Saga):**
1. `POST /api/v1/orders` → order-service prices items from the catalog, then saves the order as `PENDING` **and** an `OrderCreated` outbox row in one transaction.
2. The outbox relay publishes `order.created`; payment-service consumes it, decides the payment, and stages `payment.approved` or `payment.failed` in its own outbox.
3. order-service consumes the result → `CONFIRMED` / `PAYMENT_FAILED`; notification-service emails the customer.

**Why it survives failure:**

| Failure | What happens |
|---|---|
| Rollback after the event was "sent" | Impossible — the event is a row in the same transaction, so it rolls back with the order. |
| Broker down when an order is placed | The order still commits; the relay retries until the broker returns. |
| Kafka redelivers an event | Consumers are idempotent: payment-service skips an order it has already paid, and order-service only moves an order out of `PENDING`. |
| A record cannot be processed at all | Three retries, then it is parked on `<topic>.dlt` instead of being dropped. |

## Tech stack

| Layer | Technology |
|---|---|
| **Runtime** | Java 21, Spring Boot 3.4.1, Spring Cloud 2024.0 |
| **API Gateway** | Spring Cloud Gateway, JJWT 0.12 (JWT validated once at the edge) |
| **Messaging** | Apache Kafka in KRaft mode (no ZooKeeper) — topics `order.created`, `payment.approved`, `payment.failed`, plus a `.dlt` per topic |
| **Event reliability** | Transactional outbox + polling relay (`SKIP LOCKED`), idempotent consumers, dead-letter topics |
| **Testing** | JUnit 5 (`@Nested`, `@ParameterizedTest`), AssertJ, Mockito, Testcontainers, Pact, ArchUnit |
| **Persistence** | PostgreSQL 16 (one DB per stateful service), Redis 7 (catalog cache-aside), Flyway migrations |
| **Service comms** | Spring Cloud OpenFeign (sync) + Kafka (async) |
| **Resilience** | Resilience4j circuit breaker + fallbacks on the Feign clients |
| **Observability** | Micrometer → Prometheus + Grafana; JSON logs → Loki (Promtail); OpenTelemetry → Jaeger |
| **Testing** | JUnit 5, Mockito, Testcontainers (PostgreSQL/Kafka/Redis), Pact (consumer + provider) |
| **API docs** | SpringDoc OpenAPI / Swagger UI per service |
| **Build & deploy** | Maven multi-module, Docker Compose, Helm, GitHub Actions CI/CD (build → test → Trivy → push to GHCR) |

## Running locally

**Prerequisites:** Docker (Docker Desktop with BuildKit). Java 21 + Maven are only needed to run tests or a service outside Docker.

```bash
# Build every image and start the whole system (infra + 6 services)
docker compose up -d --build

# Tail logs / stop
docker compose logs -f order-service
docker compose down
```

The first build compiles all modules inside the images, so it takes a few minutes; subsequent starts are fast.

| Tool | URL |
|---|---|
| API Gateway (entry point) | http://localhost:8080 |
| Swagger UI (per service) | http://localhost:808x/swagger-ui.html |
| Grafana | http://localhost:3000 (admin / admin) |
| Prometheus | http://localhost:9090 |
| Jaeger (traces) | http://localhost:16686 |
| Mailhog (dev email) | http://localhost:8025 |

## Example: end to end

```bash
# 1. Register and capture the JWT
TOKEN=$(curl -s -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"marcos@orderhub.com","password":"password123","firstName":"Marcos","lastName":"Dias"}' \
  | jq -r .token)

# 2. Create a product (returns its id)
PID=$(curl -s -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Burger","description":"Cheese burger","price":25.90,"category":"food"}' \
  | jq -r .id)

# 3. Place an order — the client sends only productId + quantity; the server prices it
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"productId\":\"$PID\",\"quantity\":2}]}" | jq

# 4. A moment later, the Saga has run — check the payment result
#    (order status becomes CONFIRMED; an email lands in Mailhog)
curl -s http://localhost:8080/api/v1/orders/{orderId}/payment \
  -H "Authorization: Bearer $TOKEN" | jq
```

## API reference

All traffic goes through the gateway. Protected routes require `Authorization: Bearer <token>`.

**Auth** — `POST /auth/register`, `POST /auth/login` → `{ token, tokenType, email, role }`

**Catalog** — `GET /api/v1/products` (paginated), `GET /api/v1/products/{id}`, `GET /api/v1/products/category/{cat}`, and `POST` / `PUT` / `DELETE` (auth required)

**Orders** *(auth required)*
```
POST /api/v1/orders            body: { "items": [ { "productId", "quantity" } ] }  → 201, triggers the Saga
GET  /api/v1/orders/{id}       order with status
GET  /api/v1/orders/{id}/payment   payment detail (sync call to payment-service, Pact-tested)
GET  /api/v1/orders/my         current user's orders
```

**Payments** *(auth required)* — `GET /api/v1/payments/{id}`, `GET /api/v1/payments/order/{orderId}`

## Testing

```bash
./mvnw verify                    # unit + slice + contract + architecture tests, and the coverage gate
./mvnw verify -DexcludedGroups=  # also runs the Testcontainers integration tests (needs Docker)
```

`./mvnw verify` passes the 80% JaCoCo gate without Docker — the integration tests add
end-to-end confidence, they are not load-bearing for coverage.

| Kind | What it covers |
|---|---|
| **Unit** | Services, the saga's idempotency guards, the outbox relay, resilience fallbacks. |
| **Slice** (`@WebMvcTest`) | The HTTP edge: validation, status codes, JSON contract — no database. |
| **Architecture** (ArchUnit) | Layering, no service-to-service package dependency, entities never leaving the service layer, only the outbox publishing to Kafka. |
| **Contract** (Pact) | `order-service` pins the `GET /payments/order/{id}` contract it really calls; `payment-service` verifies it. |
| **Integration** (Testcontainers) | Real PostgreSQL/Kafka/Redis, tagged `@Tag("integration")`. No infrastructure is mocked. |

The architecture tests are the ones worth a second look: the rules in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) are executable, so the diagram cannot quietly
drift from the code.

## Key design decisions

- **Async for commands, sync for queries.** Payment runs after the order exists and fans out to multiple consumers → Kafka. Reading a price or a payment detail is immediate → REST/Feign.
- **Never trust a client price.** The order request carries only `productId` + `quantity`; the server resolves the authoritative price from the catalog and computes the total.
- **Circuit breaker where it works.** Resilience4j wraps the Feign proxies. Catalog down → fail fast (`503`, you can't price blindly); payment query down → degrade to `UNKNOWN` status.
- **Consistent errors.** Every service returns RFC 7807 `application/problem+json`.
- **One DB per service, JWT once at the gateway.** Boundaries are enforced; downstream services trust forwarded `X-User-*` headers.

Full trade-off discussion in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Project structure

```
orderhub/
├── common-kafka/           String producer template + dead-letter error handler
├── common-outbox/          Transactional outbox: DomainEvent, recorder, relay
├── api-gateway/            Spring Cloud Gateway + JWT filter
├── auth-service/           Register/login, JWT, PostgreSQL
├── catalog-service/        Product CRUD, Redis cache-aside
├── order-service/          Order creation, Feign clients, outbox relay + Saga consumer
├── payment-service/        order.created consumer, payment.* producer, Pact provider
├── notification-service/   payment.approved consumer, email via Mailhog
├── infra/                  prometheus, grafana, loki, promtail configs
├── helm/orderhub/          Helm chart (deploys the 6 services to Kubernetes)
├── docs/ARCHITECTURE.md    Full architecture guide
└── docker-compose.yml      The entire system, one command
```

## Roadmap

Seed data + HTTP collection · committed Grafana dashboards · compensating transaction (stock reservation released on `payment.failed`) · rate limiting at the gateway · consolidate k8s/Helm · Pact Broker in CI. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#10-evolution-roadmap).
