# OrderHub — Distributed Order Processing Platform

Production-grade Java microservices portfolio project demonstrating event-driven architecture, distributed tracing, and cloud-native deployment patterns.

## Architecture

```
                           ┌─────────────────────────────────────────────┐
                           │              api-gateway :8080               │
                           │     Spring Cloud Gateway + JWT validation     │
                           └──────────┬──────────────────────────────────┘
                                      │  routes validated requests
                 ┌────────────────────┼──────────────────────┐
                 │                    │                       │
        ┌────────▼──────┐   ┌────────▼──────┐   ┌───────────▼──────┐
        │ auth-service  │   │catalog-service│   │  order-service   │
        │    :8081      │   │    :8082      │   │      :8083       │
        │  JWT + RBAC   │   │ Redis cache   │   │  Kafka producer  │
        │  PostgreSQL   │   │ PostgreSQL    │   │  OpenFeign+CB    │
        └───────────────┘   └───────────────┘   └────────┬─────────┘
                                                          │ order.created
                                                ┌─────────▼─────────────────┐
                                                │      Apache Kafka          │
                                                └──┬──────────────────────┬─┘
                                                   │ payment.approved/     │
                                          ┌────────▼──────┐   ┌───────────▼──────┐
                                          │payment-service│   │notification-svc  │
                                          │    :8084      │   │     :8085        │
                                          │  Saga pattern │   │  Spring Mail     │
                                          │  PostgreSQL   │   │  Mailhog (dev)   │
                                          └───────────────┘   └──────────────────┘
```

**Event flow (Saga choreography):**
1. `POST /api/v1/orders` → order-service persists order (PENDING) → publishes `order.created`
2. payment-service consumes → processes → publishes `payment.approved` or `payment.failed`
3. order-service updates status (CONFIRMED / PAYMENT_FAILED)
4. notification-service sends confirmation email

## Tech Stack

| Layer | Technology |
|---|---|
| **Runtime** | Java 21, Spring Boot 3.4.1, Spring Cloud 2024.0 |
| **API Gateway** | Spring Cloud Gateway, JJWT 0.12 |
| **Messaging** | Apache Kafka — topics: `order.created`, `payment.approved`, `payment.failed` |
| **Persistence** | PostgreSQL 16 (per-service DB), Redis 7 (catalog cache), Flyway migrations |
| **Resilience** | Resilience4j (circuit breaker + fallback on catalog calls from order-service) |
| **Service comms** | Spring Cloud OpenFeign (order → catalog, sync) + Kafka (async/event-driven) |
| **Observability** | Micrometer + Prometheus, Grafana dashboards, Loki logs, OpenTelemetry + Jaeger tracing |
| **Logging** | Structured JSON via logstash-logback-encoder → Loki |
| **Testing** | JUnit 5, Mockito, Testcontainers (PostgreSQL + Kafka + Redis), Pact (contract tests) |
| **Quality** | JaCoCo ≥ 80% line coverage enforced on every module |
| **API Docs** | SpringDoc OpenAPI — Swagger UI aggregated at gateway |
| **Build** | Maven multi-module, Docker Compose (dev), GitHub Actions CI/CD |
| **Deploy** | Kubernetes manifests + Helm chart (all 6 services) |

## Key Design Decisions

**Saga choreography over orchestration** — no central coordinator; each service reacts to events and publishes its own. Simpler to scale and avoids a single point of failure.

**Per-service database** — auth, catalog, order, and payment each own their schema. Cross-service reads go through the API, never direct DB access. Enables independent deployments.

**JWT at gateway** — tokens are validated once at the gateway; downstream services trust the injected `X-User-Id` / `X-User-Email` headers. Removes security coupling from business services.

**Circuit breaker on catalog calls** — order-service calls catalog-service via Feign with Resilience4j. On failure the fallback uses the price provided in the request, so order creation degrades gracefully.

## Project Structure

```
orderhub/
├── api-gateway/                Spring Cloud Gateway + JWT filter
├── auth-service/               Register/login, RBAC, Flyway migration
├── catalog-service/            Product CRUD, Redis cache (10 min TTL)
├── order-service/              Order creation, Kafka producer, Saga consumer
├── payment-service/            OrderCreated consumer, PaymentApproved/Failed producer
├── notification-service/       PaymentApproved consumer, email via Spring Mail
├── infra/
│   ├── prometheus/             Scrape config for all 6 services
│   ├── grafana/provisioning/   Pre-provisioned Prometheus + Loki + Jaeger datasources
│   ├── loki/                   Loki config for log aggregation
│   └── promtail/               Log shipping from containers
├── k8s/                        Kubernetes manifests (Deployment + Service per microservice)
├── helm/orderhub/              Helm chart with configurable values.yaml
└── .github/workflows/ci.yml    Build → unit tests → integration tests → Trivy scan
```

## Running Locally

**Prerequisites:** Docker, Java 21, Maven 3.9+

```bash
# 1. Start all infrastructure (Kafka, PostgreSQL x4, Redis, Mailhog, Prometheus, Grafana, Loki, Jaeger)
docker compose up -d

# 2. Build all modules
mvn clean package -DskipTests

# 3. Run a service (example)
mvn spring-boot:run -pl auth-service
```

**Infrastructure endpoints:**

| Tool | URL |
|---|---|
| API Gateway | http://localhost:8080 |
| Swagger UI (aggregated) | http://localhost:8080/swagger-ui.html |
| Grafana | http://localhost:3000 (admin / admin) |
| Prometheus | http://localhost:9090 |
| Jaeger UI | http://localhost:16686 |
| Mailhog (dev email) | http://localhost:8025 |

## Testing

```bash
# Unit tests (all modules)
mvn test

# Integration tests — spins up real Kafka + PostgreSQL via Testcontainers
mvn verify -Dgroups=integration

# Coverage report (target/site/jacoco/index.html per module)
mvn verify jacoco:report

# Single service
mvn test -pl order-service
```

**Test strategy:**
- Unit tests mock repositories and Kafka producers (Mockito)
- Integration tests use Testcontainers for real PostgreSQL and Kafka — no mocks of infrastructure
- Pact contract tests: order-service (consumer) defines the expected API contract; payment-service (provider) verifies it on every build

## CI/CD

GitHub Actions pipeline (`.github/workflows/ci.yml`):

```
push → build (mvn package) → unit tests → integration tests → Trivy security scan
```

## Kubernetes Deployment

```bash
# Apply namespace + infra
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/infra/

# Apply all services
kubectl apply -f k8s/auth-service/
kubectl apply -f k8s/catalog-service/
kubectl apply -f k8s/order-service/
kubectl apply -f k8s/payment-service/
kubectl apply -f k8s/notification-service/
kubectl apply -f k8s/api-gateway/

# Or deploy everything with Helm
helm install orderhub ./helm/orderhub --namespace orderhub --create-namespace
```

## API Reference

All requests go through the gateway at `http://localhost:8080`. Protected routes require `Authorization: Bearer <token>`.

**Auth**
```
POST /auth/register   { "email", "password", "firstName", "lastName" }
POST /auth/login      { "email", "password" }  →  { "token", "expiresIn", "email", "role" }
```

**Catalog** *(public)*
```
GET  /api/v1/products                  paginated list of available products
GET  /api/v1/products/{id}
GET  /api/v1/products/category/{cat}
POST /api/v1/products                  create (auth required)
PUT  /api/v1/products/{id}             update (auth required)
DELETE /api/v1/products/{id}           delete (auth required)
```

**Orders** *(auth required)*
```
POST /api/v1/orders         create order  →  triggers Saga
GET  /api/v1/orders/{id}    get by id
GET  /api/v1/orders/my      list my orders
```

**Payments** *(auth required)*
```
GET /api/v1/payments/{id}
GET /api/v1/payments/order/{orderId}
```

## Environment Variables

| Variable | Default | Used by |
|---|---|---|
| `JWT_SECRET` | base64 key | api-gateway, auth-service |
| `DB_URL` | `jdbc:postgresql://localhost:543x/...` | all DB services |
| `DB_USER` / `DB_PASS` | `orderhub` / `orderhub123` | all DB services |
| `KAFKA_SERVERS` | `localhost:9092` | order, payment, notification |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | catalog-service |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` | notification-service |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | all services |
