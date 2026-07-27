# OrderHub — Resume & LinkedIn copy

Truthful descriptions of what was actually built. Pick the format you need.

---

## 🇧🇷 Português

### Linha de currículo (uma frase)
Plataforma distribuída de processamento de pedidos com 5 microsserviços Spring Boot (Java 21), comunicação síncrona (REST/Feign) e assíncrona (Kafka) via Saga por coreografia, com resiliência, testes de contrato e observabilidade completa.

### Bullets de currículo
- Projetei e implementei **5 microsserviços Spring Boot (Java 21)** atrás de um **API Gateway** com validação de JWT na borda e roteamento.
- Modelei o ciclo de vida do pedido como uma **Saga por coreografia sobre Apache Kafka** (order → payment → notification), separando **comandos assíncronos** de **consultas síncronas** (OpenFeign).
- Implementei **resiliência com Resilience4j** (circuit breaker + fallbacks) nos clients Feign — falha rápida quando o catálogo cai, degradação graciosa nas consultas de pagamento.
- Garanti **observabilidade ponta a ponta**: métricas (Micrometer/Prometheus/Grafana), logs estruturados em JSON (Loki/Promtail) e **tracing distribuído** (OpenTelemetry/Jaeger) em todos os serviços.
- Escrevi uma pirâmide de testes real: **unitários (Mockito)**, **integração com Testcontainers** (PostgreSQL/Kafka/Redis) e **testes de contrato com Pact** (consumer + provider).
- Empacotei tudo com **Docker Compose** (sobe o sistema inteiro em um comando) e **CI no GitHub Actions** com build, testes e scan de segurança (Trivy).

### Descrição para LinkedIn / portfólio
**OrderHub — Plataforma Distribuída de Processamento de Pedidos**
Projeto de portfólio que demonstra arquitetura de microsserviços orientada a eventos em Java. Cinco serviços Spring Boot (autenticação, catálogo, pedidos, pagamentos e notificações) atrás de um API Gateway com JWT. O fluxo de pagamento é uma **Saga por coreografia** sobre Kafka, e as leituras entre serviços usam REST/OpenFeign com **circuit breaker (Resilience4j)**. Cada serviço tem seu próprio banco PostgreSQL (Flyway), o catálogo usa **cache-aside com Redis**, e todos expõem **métricas, logs em JSON e traces distribuídos** (Prometheus/Grafana, Loki, Jaeger). Cobertura por testes unitários, de integração (Testcontainers) e de contrato (Pact). Sobe inteiro com `docker compose up`.
Foco do projeto: **qualidade e decisões de engenharia bem justificadas**, não quantidade de ferramentas.

---

## 🇺🇸 English

### Resume line (one sentence)
Distributed order-processing platform built as 5 Spring Boot microservices (Java 21) with synchronous (REST/Feign) and asynchronous (Kafka) communication via a choreography Saga, plus resilience, contract testing, and full observability.

### Resume bullets
- Designed and built **5 Spring Boot microservices (Java 21)** behind an **API Gateway** that validates JWTs at the edge and routes traffic.
- Modeled the order lifecycle as a **choreography Saga over Apache Kafka** (order → payment → notification), separating **asynchronous commands** from **synchronous queries** (OpenFeign).
- Added **Resilience4j circuit breakers + fallbacks** on the Feign clients — fail fast when the catalog is down, degrade gracefully on payment lookups.
- Delivered **end-to-end observability**: metrics (Micrometer/Prometheus/Grafana), structured JSON logs (Loki/Promtail), and **distributed tracing** (OpenTelemetry/Jaeger) across every service.
- Wrote a real test pyramid: **unit (Mockito)**, **integration with Testcontainers** (PostgreSQL/Kafka/Redis), and **Pact contract tests** (consumer + provider).
- Containerized the whole system with **Docker Compose** (one-command startup) and a **GitHub Actions CI** pipeline with build, tests, and a Trivy security scan.

### LinkedIn / portfolio description
**OrderHub — Distributed Order Processing Platform**
A portfolio project demonstrating event-driven microservices in Java. Five Spring Boot services (auth, catalog, orders, payments, notifications) sit behind a JWT-validating API Gateway. The payment flow is a **choreography Saga** over Kafka, while cross-service reads use REST/OpenFeign with a **Resilience4j circuit breaker**. Each service owns its PostgreSQL database (Flyway), the catalog uses **Redis cache-aside**, and every service ships **metrics, JSON logs, and distributed traces** (Prometheus/Grafana, Loki, Jaeger). Covered by unit, integration (Testcontainers), and contract (Pact) tests. The entire stack starts with `docker compose up`.
The emphasis is on **engineering quality and well-justified decisions**, not on the number of tools.
