# msrbooknest

**A portfolio-ready online bookstore built with React, Java 17, Spring Boot microservices, Spring Data JPA/Hibernate, Spring Security JWT, MySQL, and Kafka.**

The demo covers the customer journey from account creation and search through cart, inventory reservation, asynchronous payment, and order history. It also includes a used-book buyback workflow with photo submission, versioned quotations, mock video and physical reviews, DTDC tracking, returns, disputes, and a single mock payout record. Checkout uses a deterministic NPCI-style simulator so both approved and declined payment paths can be demonstrated without contacting a financial network.

## Run the full demo

Install Docker Desktop, open a terminal in this repository, then run:

```bash
docker compose up -d --build
./scripts/demo-check.sh
```

Open **[http://localhost:5173](http://localhost:5173)**. Open the [buyback demo](http://localhost:5173/#buyback) directly, or choose **Sell a book** in the storefront. Compose starts React, six Spring Boot services, MySQL, and Kafka. The first build can take a few minutes.

For a guided interview walkthrough, seeded account details, API links, troubleshooting, and shutdown commands, see [the demo guide](docs/docker-deployment.md). For architecture and tradeoffs, see [the architecture notes](docs/architecture.md) and [the interview walkthrough](docs/interview-demo-guide.md).

## Public browser demo

The React storefront is published with GitHub Pages at <https://srinivasmaram8.github.io/Srinivas/>. The Pages build uses a browser-only mock adapter, so sample catalog, cart, checkout, account, and used-book buyback actions are saved in that browser. No real email/SMS, courier, or payment is sent. The Spring Boot services, MySQL, and Kafka remain available through the local Docker Compose demo above; GitHub Pages does not run those services.

Demo sign-in:

- Customer: `reader@msrbooknest.demo` / `ReaderDemo@123`
- Admin: `admin@msrbooknest.demo` / `AdminDemo@123`

The Pages deployment runs automatically when the storefront or its workflow changes on `main`.

## What the demo shows

- **React storefront:** browse and search, suggestions, account registration/sign-in, cart, checkout, and order history.
- **Spring Security + JWT:** BCrypt passwords, signed access tokens, user-scoped shopping data, and admin-only catalog/order operations.
- **Spring Data JPA + Hibernate:** service-owned schemas and persistence for identity, catalog/inventory, carts, orders/outbox, and payments.
- **Kafka checkout workflow:** order/payment events, an Order-service transactional outbox, idempotent payment processing, and inventory confirmation or release.
- **Precise currency handling:** `BigDecimal` in Java and decimal columns in MySQL from book prices through payment events.
- **Observable services:** restricted Spring Boot Actuator health, liveness, and readiness endpoints.
- **Mock UPI/NPCI integration:** reproducible approval and decline scenarios with clearly labeled simulator references.
- **Used-book buyback:** customer and admin dashboards, photo uploads, quote versions requiring explicit customer approval, simulated DTDC shipments, inspection evidence, return tracking, dispute decisions, and duplicate-safe mock payout.
- **Container build:** multi-stage Java images, Maven dependency cache, and a non-root runtime user.

## Service map

| Service | Port | Owns |
|---|---:|---|
| React storefront | 5173 | Browser experience |
| Auth | 8081 | Registration, OTP challenges, login, JWT |
| Catalog | 8082 | Books and inventory reservations |
| Order | 8083 | Checkout, order state, transactional outbox |
| Cart | 8084 | Customer shopping carts |
| Payment | 8085 | Payment transactions and mock gateway |
| Buyback | 8086 | Used-book submissions, quote history, inspection, returns, disputes, mock payout |
| MySQL | 3306 | Local database server with separate service schemas |
| Kafka | 9092 | Local event broker |

Each Spring service has a Swagger UI at `http://localhost:<port>/swagger-ui/index.html` and a health endpoint at `http://localhost:<port>/actuator/health`.

## Checkout story

The Order service gets the authoritative prices and reserves stock through Catalog. It commits a pending order and a payment-request outbox record together, then publishes that event to Kafka. Payment processes the request once per order ID and emits a success or failure event. Order updates the status and emits a follow-up event so Catalog commits or releases the held inventory.

Enter **`success@upi`** to approve or **`fail@upi`** to decline. The NPCI adapter is a local simulator: it creates a demo reference and never moves money.

## Repository map

- `customer-web-react/` — active React/Vite application.
- `auth-service/`, `catalog-service/`, `cart-service/`, `order-service/`, `payment-service/`, `buyback-service/` — independently deployable Spring Boot services.
- `bookstore-events/` — versioned Kafka event contracts.
- `docs/` — architecture, deployment, and interview notes.
- `enterprise-bookstore/` and `customer-web/` — earlier learning experiments, not part of the active Compose stack.

## Demo security note

Compose defaults, local demo accounts, OTP display, database credentials, and the JWT signing key are for a laptop demo only. Do not expose this stack publicly with its defaults. Replace demo secrets, configure real email/SMS delivery, add a managed identity/key strategy, and integrate a certified payment provider before any production deployment.
