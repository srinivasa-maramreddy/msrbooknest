# msrbooknest architecture

## System context

```mermaid
flowchart LR
    Shopper[Shopper] --> Web[React storefront]
    Web -->|login and OTP| Auth[Auth service\nSpring Security + JWT]
    Web -->|public browse| Catalog[Catalog service]
    Web -->|Bearer JWT| Cart[Cart service]
    Web -->|Bearer JWT| Order[Order service]
    Web -->|Bearer JWT| Payment[Payment service]
    Web -->|Bearer JWT| Buyback[Buyback service]
    Cart -->|book details| Catalog
    Order -->|stock hold and server price| Catalog
    Order -->|load and clear cart| Cart
    Order -->|pending order response| Web
    Payment -->|server-side API| Gateway[Razorpay Test Mode]
    Payment -->|verified payment success| Kafka[(Kafka)]
    Kafka -->|payment result| Order
    Order -->|paid or cancelled| Kafka
    Kafka -->|commit or release stock| Catalog
    Buyback -->|status event| Kafka
    Auth --> AuthDB[(auth_db)]
    Catalog --> CatalogDB[(catalog_db)]
    Cart --> CartDB[(cart_db)]
    Order --> OrderDB[(order_db)]
    Payment --> PaymentDB[(payment_db)]
    Buyback --> BuybackDB[(buyback_db)]
```

The local stack runs six Java 17/Spring Boot services, the React/Vite web app, MySQL, and Kafka in KRaft mode. Each service owns its schema and persistence boundary. The schemas share one local MySQL server in Compose; they are not separate database clusters.

## Service responsibilities

| Service | Data it owns | Main responsibility |
|---|---|---|
| Auth | Users, OTP challenges, registration sessions | Verify demo registration steps, store BCrypt hashes, issue JWTs |
| Catalog | Books, inventory reservations | Search books, supply authoritative prices, hold and settle stock |
| Cart | Customer cart items | Maintain a shopper's cart and refresh book details from Catalog |
| Order | Orders, outbox events | Validate checkout, coordinate stock hold, track fulfillment/payment status |
| Payment | Payment transactions, payment attempts, webhook event IDs | Create provider orders, enforce order/attempt idempotency, verify provider responses and signed webhooks |
| Buyback | Buyback cases, quote history, photos, activity, payout ledger | Manage customer/admin buyback workflow and emit status events |

`bookstore-events` contains the JSON event records and topic names shared between producer and consumer services. REST calls are used for synchronous reads and inventory reservation; Kafka carries the longer-running payment and inventory settlement workflow.

## Checkout sequence

```mermaid
sequenceDiagram
    actor Shopper
    participant UI as React
    participant Order as Order service
    participant Catalog as Catalog service
    participant DB as Order DB
    participant Payment as Payment service
    participant Gateway as Razorpay Test Mode
    participant Kafka

    Shopper->>UI: Submit checkout with idempotency key
    UI->>Order: POST /api/orders (JWT)
    Order->>Catalog: Reserve stock and return server prices
    Catalog-->>Order: Held reservation + quote
    Order->>DB: Save PENDING_PAYMENT order
    DB-->>Order: Commit
    Order-->>UI: Pending order
    UI->>Payment: POST /api/payments/create with attempt key
    Payment->>Gateway: Create provider order once per internal order
    Gateway-->>Payment: Provider order ID
    Payment-->>UI: UPI checkout parameters
    UI->>Gateway: Complete test payment
    Gateway-->>UI: Signed callback
    UI->>Payment: POST /api/payments/verify
    Payment->>Gateway: Fetch authoritative payment status
    Gateway-->>Payment: Captured payment details
    Payment->>Kafka: Publish verified success
    Kafka->>Order: Consume payment result
    Order->>DB: Update status + enqueue follow-up outbox event
    Order->>Kafka: Publish paid or cancelled
    Kafka->>Catalog: Commit or release held stock
    UI->>Order: Read owned order/payment status
    Order-->>UI: PAID after verified success
```

### Consistency and failure handling

- **Price trust:** the browser sends book IDs and quantities. Catalog resolves the price; Order ignores client-supplied prices.
- **Idempotency:** the browser reuses a stable checkout key for an unchanged cart/address. Order stores a SHA-256 fingerprint and rejects changed details under a reused key. Payment stores one provider order per internal order plus uniquely keyed attempts; network retries return the same attempt, and a fresh attempt requires a confirmed failed/cancelled state. Duplicate webhook IDs and order transitions are handled safely.
- **Transactional outbox:** Order saves its paid state and inventory-settlement event in the same JPA transaction. A scheduled publisher retries unsent rows; duplicate payment callbacks do not transition an already-paid order again.
- **Inventory compensation:** Catalog reduces available stock when it creates a hold. A verified paid event commits the hold; the reservation's 20-minute expiry releases it if checkout is abandoned.
- **Currency correctness:** Java uses `BigDecimal`, request validation limits currency precision to two fraction digits, and JPA columns use `DECIMAL(12,2)`.
- **Event versioning:** event records carry `eventId`, `schemaVersion`, and `occurredAt`; topic names include `.v1`.

The workflow is eventually consistent: the browser receives a pending order first and checks the payment/order state while Kafka applies the verified result and inventory settlement. The current local project has one Kafka broker. The order outbox has retry state, while full multi-replica row claiming, a dedicated dead-letter policy, distributed tracing, and production Kafka monitoring remain sensible next steps.

## Authentication and authorization

Auth verifies BCrypt password hashes and issues an HS256 JWT with account subject, email, role, and expiry claims. Catalog, Cart, Order, Payment, and Buyback are stateless JWT resource servers. Public product browsing is allowed; shopper-specific cart/order/buyback actions require a token; catalog writes, order administration, and buyback review, inspection, return dispatch, and dispute resolution are restricted to admins.

The local registration journey collects name, mobile number, email, and password, then verifies the email before account creation. Mobile numbers remain available for sign-in but are not OTP destinations. Compose uses a demo setting that displays email codes in the storefront instead of delivering them. Real email delivery is available through SMTP; SMS OTP integration is not included. Demo secrets, CORS, and the shared HMAC signing key must be replaced before public hosting.

## API and operations surface

| Service | Port | Main paths | Docs / health |
|---|---:|---|---|
| Auth | 8081 | `/api/auth/register/*`, `/api/auth/login` | `/swagger-ui/index.html`, `/actuator/health` |
| Catalog | 8082 | `/api/products`, `/api/admin/products`, `/api/books/inventory/*` | same paths on port 8082 |
| Cart | 8084 | `/api/cart` | same paths on port 8084 |
| Order | 8083 | `/api/orders`, `/api/orders/me` | same paths on port 8083 |
| Payment | 8085 | `/api/payments/create`, `/api/payments/verify`, `/api/payments/orders/{id}/status`, `/api/payments/webhook` | same paths on port 8085 |
| Buyback | 8086 | `/api/buybacks`, `/{id}/quote`, `/{id}/inspection`, `/{id}/dispute/resolve` | same paths on port 8086 |

Only Actuator health endpoints are exposed. Health, liveness, and readiness are public for local container/runtime checks; other Actuator endpoints are not enabled. Each service also exposes OpenAPI/Swagger documentation. There is no API gateway in the demo; the React app calls local service origins through its Nginx proxy.

## Kafka topics

| Topic | Producer → consumer | Purpose |
|---|---|---|
| `bookstore.payments.succeeded.v1` | Payment → Order | Mark order paid and retain provider reference |
| `bookstore.orders.paid.v1` | Order → Catalog | Commit held inventory |
| `bookstore.orders.cancelled.v1` | Order → Catalog | Release held inventory |
| `buyback.status-events` | Buyback → consumers | Publish buyback case status changes |

## Deliberate demo boundaries

- UPI checkout uses Razorpay Test Mode when explicitly configured. Live credentials are disabled by default. See [the setup and verification flow](razorpay-test-checkout.md).
- A verified payment event arriving for a cancelled order leaves it cancelled, records `paymentReconciliationStatus=REFUND_REQUIRED`, and does not enqueue fulfilment. The demo records this for manual refund reconciliation; it does not automatically issue a refund.
- Buyback video verification, courier tracking, payout, return shipping, and dispute review are simulated. Book and inspection photos persist in the local `buyback_db`; no camera, courier, bank, or payment provider is contacted for buyback.
- OTP codes are shown only because Compose is configured for local demo mode. Real email delivery needs valid SMTP configuration and must disable code display.
- A single local broker and shared MySQL server make the project easy to run. Production would use resilient managed infrastructure, service credentials, migrations, monitoring, and a defined recovery policy.
