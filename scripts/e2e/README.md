# OrderHub — E2E smoke suite

Drives the full docker-compose stack through `api-gateway` to prove the choreographed
saga works end to end: register/login -> admin creates a product -> user places an
order -> outbox -> Kafka -> payment-service -> order CONFIRMED -> stock decremented ->
notification-service email in Mailhog -> gateway rate limiter under burst load.

Runs in CI as its own workflow, `.github/workflows/e2e.yml` ("E2E Smoke"), on pushes to
`main`, on `workflow_dispatch`, and on pull requests that touch service/build code. It is
separate from `ci.yml` because it is slow (full image builds + a real Kafka broker) —
`ci.yml`'s Testcontainers integration tests already cover each service in isolation; this
suite is the one that proves the wiring between them.

## Running locally

Requires Docker, `curl`, and `jq`.

```bash
# from the repo root
cp .env.example .env
# edit .env and set JWT_SECRET, e.g.:
#   echo "JWT_SECRET=$(openssl rand -base64 48)" > .env

docker compose up -d --build --wait

./scripts/e2e/smoke.sh
```

`BASE_URL` (default `http://localhost:8080`) and `MAILHOG_URL` (default
`http://localhost:8025`) can be overridden if the stack is exposed elsewhere:

```bash
BASE_URL=http://localhost:8080 MAILHOG_URL=http://localhost:8025 ./scripts/e2e/smoke.sh
```

Tear down afterwards with `docker compose down -v` if you want a clean slate (this also
resets Postgres volumes, so the next run starts from Flyway's migrations again).

## Expected duration

- Locally, with images already built: the script itself runs in roughly 1–3 minutes —
  most of that is polling for the saga to confirm the order (up to 90s) and for the
  confirmation email to land in Mailhog (up to 60s), both of which usually resolve much
  faster than their timeouts.
- In CI, `docker compose up -d --build` on a cold cache (six services, each compiling its
  own Maven reactor slice inside its Dockerfile) is the dominant cost — budget 10–15
  minutes for the build+start, on top of the script's own 1–3 minutes. The workflow's job
  timeout is 25 minutes.

## What each step proves

1. Gateway health check — the stack is up and reachable.
2. Register + login (user and seeded admin) — auth-service issues JWTs, api-gateway
   forwards them.
3. Admin creates a product with stock; a plain user gets 403 — catalog-service's
   ADMIN-only mutation guard.
4. User creates an order, polled until `CONFIRMED` — the transactional outbox relay,
   Kafka delivery, payment-service's saga consumer, and order-service's own consumer all
   worked.
5. Product stock decremented by the ordered quantity — catalog-service's
   `OrderCreatedConsumer` / `StockReservationService` reserved stock from the same event.
6. Mailhog received a confirmation email addressed to the test user —
   notification-service consumed `PaymentApproved` and sent mail.
7. Hammering `/auth/login` with bad credentials returns at least one `429` — the
   gateway's Redis-backed `RequestRateLimiter` on `/auth/**`.
