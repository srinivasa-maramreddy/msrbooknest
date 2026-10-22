# msrbooknest: project status and next steps

The current localhost demo and its design are documented in the [Docker guide](docker-deployment.md) and [architecture notes](architecture.md).

## Implemented

- React/Vite storefront with catalog browsing/search, registration/login, shopping cart, checkout, and order history.
- Six Spring Boot services with Spring Security, stateless JWT validation, BCrypt password hashes, validation, OpenAPI, and Actuator health probes.
- Spring Data JPA/Hibernate persistence with separate auth, catalog, cart, order, payment, and buyback schemas.
- Catalog-authoritative pricing and decimal money handling using `BigDecimal` and `DECIMAL(12,2)`.
- Idempotent order checkout, inventory holds with expiry/compensation, and an Order-service transactional outbox.
- Kafka payment request/result and inventory settle/release events.
- Deterministic simulated UPI/card processing, including success and decline outcomes.
- Used-book buyback with photo uploads, versioned quotations, mock video and physical inspection, DTDC tracking, return/dispute decisions, unique payout ledger, and Kafka status events.
- Docker Compose configuration for the complete demo stack.

## Run and verify manually

```bash
docker compose up -d --build
docker compose ps
```

Open `http://localhost:5173`, sign in as the demo reader, then complete one checkout with `success@upi` and one with `fail@upi`. Check that the order state changes asynchronously and that service health endpoints return `UP`. The steps are in the [demo guide](docker-deployment.md).

## Good next increments for a production-shaped version

1. Add automated unit and integration tests around auth, order idempotency, inventory compensation, and duplicate Kafka delivery.
2. Add a schema migration tool and a migration for existing money columns before changing schema policy away from `ddl-auto: update`.
3. Add outbox row claiming/locking for multiple Order replicas, exponential retry limits, dead-letter routing, and a replay procedure.
4. Add trace/correlation IDs across REST and Kafka, structured logs, metrics, and dashboards for outbox age, consumer lag, and stuck pending orders.
5. Separate OTP dev-code display from delivery configuration; add rate limits, attempt limits, expiry policy, and abuse monitoring.
6. Replace the demo JWT fallback and shared MySQL root account with managed secrets, key rotation, least-privilege database users, and a hardened identity provider.
7. Replace mock payments only after selecting a certified provider and documenting its security, webhook, reconciliation, and refund requirements.

## Not part of the active Compose stack

The `enterprise-bookstore` monolith, old static storefront, and AWS/Keycloak infrastructure experiments are kept for reference. The active system is the React app plus the six services in the root `docker-compose.yml`.
