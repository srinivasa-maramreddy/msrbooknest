# msrbooknest

**A portfolio-ready online bookstore built with React, Java 17, Spring Boot microservices, Spring Data JPA/Hibernate, Spring Security JWT, MySQL, and Kafka.**

The demo covers the customer journey from account creation and search through cart, inventory reservation, UPI checkout, and order history. The local stack supports Razorpay Standard Checkout in test mode, with provider verification before an order is marked paid. The free hosted Pages setup instead offers a backend NPCI simulator with approved and declined paths; neither mock choice contacts a bank or moves money. It also includes a used-book buyback workflow with photo submission, versioned quotations, mock video and physical reviews, DTDC tracking, returns, disputes, and a mock payout record.

## Run the full demo

Install Docker Desktop, open a terminal in this repository, then run:

```bash
docker compose up -d --build
./scripts/demo-check.sh
```

Open **[http://localhost:5173](http://localhost:5173)**. Open the [buyback demo](http://localhost:5173/#buyback) directly, or choose **Sell a book** in the storefront. Compose starts React, six Spring Boot services, MySQL, and Kafka. The first build can take a few minutes.

For a guided interview walkthrough, seeded account details, API links, troubleshooting, and shutdown commands, see [the demo guide](docs/docker-deployment.md). Configure test credentials using [the Razorpay checkout guide](docs/razorpay-test-checkout.md). For architecture and tradeoffs, see [the architecture notes](docs/architecture.md) and [the interview walkthrough](docs/interview-demo-guide.md).

## Public browser demo

The React storefront is published with GitHub Pages at <https://srinivasa-maramreddy.github.io/msrbooknest/>. By default it uses a browser-only mock adapter, so sample catalog, cart, checkout, account, and used-book buyback actions are saved in that browser. To connect it to the real Spring APIs, set the GitHub Actions repository variable `VITE_BACKEND_BASE_URL` to the public HTTPS API gateway URL and rerun the Pages workflow. For a no-card demo deployment, see [the Render and Aiven guide](docs/no-card-deployment.md). The [Oracle Always Free guide](docs/oracle-free-deployment.md) remains available for a VM deployment, and local Docker Compose remains available for development.

Demo sign-in:

- Customer: `reader@msrbooknest.demo` / `ReaderDemo@123`
- Admin: `admin@msrbooknest.demo` / `AdminDemo@123`

The Pages deployment runs automatically when the storefront or its workflow changes on `main`. Without `VITE_BACKEND_BASE_URL`, it continues to publish the browser-only demo.

## What the demo shows

- **React storefront:** browse and search, suggestions, account registration/sign-in, cart, checkout, and order history.
- **Spring Security + JWT:** BCrypt passwords, signed access tokens, user-scoped shopping data, and admin-only catalog/order operations.
- **Spring Data JPA + Hibernate:** service-owned schemas and persistence for identity, catalog/inventory, carts, orders/outbox, and payments.
- **Kafka checkout workflow:** verified payment events, an Order-service transactional outbox, duplicate-safe order updates, and inventory confirmation.
- **Precise currency handling:** `BigDecimal` in Java and decimal columns in MySQL from book prices through payment events.
- **Observable services:** restricted Spring Boot Actuator health, liveness, and readiness endpoints.
- **UPI payment:** Razorpay test mode locally, or a server-recorded NPCI mock approval/decline on the hosted free demo. Both keep stable checkout idempotency keys and separately recorded attempts; mock results never contact a payment network.
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
| Payment | 8085 | Payment transactions, attempts, and Razorpay test gateway |
| Buyback | 8086 | Used-book submissions, quote history, inspection, returns, disputes, mock payout |
| MySQL | 3306 | Local database server with separate service schemas |
| Kafka | 9092 | Local event broker |

Each Spring service has a Swagger UI at `http://localhost:<port>/swagger-ui/index.html` and a health endpoint at `http://localhost:<port>/actuator/health`.

## Checkout story

The Order service checks the signed-in customer's cart, gets authoritative prices, and reserves stock through Catalog. It fingerprints the order details against a stable idempotency key, so exact retries return the same order and changed details using that key are rejected. The Payment service creates one provider order per internal order and stores payment attempts independently. A callback is not treated as proof of payment: the backend verifies the signature and provider status, then publishes a success event through Kafka. Order records the paid state and an outbox event lets Catalog commit the stock hold. See [the test checkout guide](docs/razorpay-test-checkout.md) for setup and failure/retry scenarios.

## Repository map

- `customer-web-react/` — active React/Vite application.
- `auth-service/`, `catalog-service/`, `cart-service/`, `order-service/`, `payment-service/`, `buyback-service/` — independently deployable Spring Boot services.
- `bookstore-events/` — versioned Kafka event contracts.
- `docs/` — architecture, deployment, and interview notes.
- `enterprise-bookstore/` and `customer-web/` — earlier learning experiments, not part of the active Compose stack.

## Demo security note

Compose defaults, local demo accounts, OTP display, database credentials, and the JWT signing key are for a laptop demo only. Do not expose this stack publicly with its defaults. Replace demo secrets, configure real email/SMS delivery, add a managed identity/key strategy, and integrate a certified payment provider before any production deployment.
