# OrderHub

Distributed order processing platform built with Java microservices.

## Services

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 8080 | JWT validation, routing (Spring Cloud Gateway) |
| auth-service | 8081 | Register, login, JWT (Spring Security + RBAC) |
| catalog-service | 8082 | Product catalog with Redis cache |
| order-service | 8083 | Order creation, Kafka producer *(coming soon)* |
| payment-service | 8084 | Saga choreography, Kafka consumer *(coming soon)* |
| notification-service | 8085 | Email notifications via Mailhog *(coming soon)* |

## Tech Stack

- **Java 21** · Spring Boot 3.4 · Spring Cloud 2024
- **Messaging:** Apache Kafka (Saga choreography)
- **Persistence:** PostgreSQL (per service) · Redis (catalog cache)
- **Observability:** Prometheus · Grafana · Loki · Jaeger
- **Testing:** JUnit 5 · Mockito · Testcontainers
- **Build:** Maven multi-module · Docker Compose · GitHub Actions

## Running locally

```bash
# Start infrastructure
docker compose up -d

# Build all modules
mvn clean package -DskipTests

# Run a service
mvn spring-boot:run -pl auth-service
```
