# OrderHub — Postman collection

1. Set `JWT_SECRET` in your `.env` (repo root) — the gateway fails to start without it — then run `docker compose up -d` and wait for every service's `/actuator/health` to report UP.
2. In Postman: **Import** `OrderHub.postman_collection.json` and `OrderHub.local.postman_environment.json`, then select the **OrderHub Local** environment (top-right dropdown).
3. Run the folders in order: **01 Auth** (registers a demo user, logs in as user and as the seeded admin) → **02 Catalog** (admin creates sample products, includes a 403-as-user example) → **03 Orders** (places an order with the products just created).
4. Open **04 Saga verification** and re-send "Poll Order Until CONFIRMED" every second or two — the saga is asynchronous (order-service → Kafka → payment-service → Kafka → order-service), so confirmation isn't instant. Once it flips to `CONFIRMED`, check Mailhog at http://localhost:8025 for the notification email.
5. **05 Docs & Ops** links the aggregated Swagger UI and the gateway health check for exploring beyond this collection.

Tokens and IDs (`userToken`, `adminToken`, `productId`, `orderId`, ...) are stashed into the environment automatically by each request's test script — you don't need to copy anything by hand.
