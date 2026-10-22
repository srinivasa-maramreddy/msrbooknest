# msrbooknest interview walkthrough

## 30-second project introduction

“msrbooknest is a React bookstore backed by six Spring Boot services. I used Spring Security and JWT for stateless authentication, Spring Data JPA for service-owned persistence, and Kafka for asynchronous checkout and buyback status events. Checkout reserves inventory, saves an order and its outbox event atomically, then processes payment asynchronously. The used-book buyback flow adds explicit quote versions, customer decisions, inspection evidence, and idempotent payouts. All external actions are simulated.”

## Live demo in about three minutes

1. Start the stack with `docker compose up -d --build` and run `./scripts/demo-check.sh`.
2. Open `http://localhost:5173` and sign in as `reader@msrbooknest.demo` / `ReaderDemo@123`.
3. Add two books, check out with UPI ID `success@upi`, and point out the initial pending state followed by `PAID`.
4. Place another order with `fail@upi`; show the failure state and explain the cancellation event that releases the inventory hold.
5. Open the Order, Payment, and Catalog logs or their Swagger UIs to trace the service boundaries and Kafka handoffs.

### Show used-book buyback

1. Open `http://localhost:5173/#buyback` and sign in as the reader. Submit a book with condition details and photos.
2. Switch to the admin demo account and complete the simulated video review. Enter a quotation and a reason.
3. Return to the reader account, review the numbered quote, accept it, and enter a mock DTDC tracking number.
4. In the admin dashboard, receive the book and pass/fail the simulated inspection. A pass creates one mock payout. A failure reveals the evidence and customer choices for return, revised quote, or dispute.
5. Demonstrate return dispatch/delivery or record a final dispute decision. Point to the activity timeline and the Kafka `buyback.status-events` stream.

Inbound shipping is customer-paid. In the demo policy, failed-inspection returns cost ₹120 and are store-paid unless a dispute decision explicitly assigns the fee to the customer.

Use the registration screen if asked about OTP. The local demo shows its phone/email OTP codes in the UI; the project has optional MSG91 and SMTP adapters for configured environments.

## Explain checkout on a whiteboard

```text
React → Order → Catalog reservation + authoritative price
                    ↓
             Order DB transaction
             (order + outbox row)
                    ↓
                   Kafka → Payment → Mock NPCI adapter
                    ↑         ↓
                    └── result event
                    ↓
              Order status + outbox
                    ↓
            Kafka → Catalog commit/release
```

Mention the three main correctness choices:

- **Outbox:** the order state and the intent to publish are committed together, avoiding the database/Kafka dual-write gap.
- **Idempotency:** `orderRequestId` is unique for orders and payment transactions, so a client retry or Kafka redelivery does not create a second charge/order.
- **Compensation:** stock is held before payment, committed after success, and released after decline or hold expiry.

Prices are resolved by Catalog, not trusted from browser input. `BigDecimal` and `DECIMAL(12,2)` preserve currency precision across the REST, JPA, and Kafka paths.

## Questions an interviewer may ask

**Why Kafka here?** Payment is a longer-running workflow. Kafka lets Order return a pending order while Payment and inventory settlement happen asynchronously. The demo also shows why consumers need idempotency and why the UI handles eventual consistency.

**Why the outbox?** Saving an order and publishing to Kafka are two independent writes. The outbox records the event inside the order transaction; a publisher sends it later and retains retry state if Kafka is unavailable.

**What if a message is delivered twice?** The order request ID is unique and payment processing looks up the existing transaction before creating another one. State transitions ignore events for orders that are no longer pending. The production version should also formalize retry limits and dead-letter/replay operations.

**Why not one database transaction across all services?** That would tightly couple service availability and data ownership. The demo uses local synchronous REST for the inventory hold, then Kafka for the payment/result/settlement workflow; the order can be pending while the asynchronous work completes.

**Is this connected to NPCI?** No. The adapter is a simulator seam. `success@upi` and `fail@upi` create deterministic demo outcomes and reference IDs; no network call or money movement occurs.

**What would you harden before production?** Add automated integration tests, migration-managed schemas, least-privilege DB accounts, outbox row locking for multiple replicas, retry/backoff and DLQ/replay, tracing/metrics/alerts, OTP rate limits, managed identity/secrets, and a certified payment integration.

## Useful links

- [Architecture and event contracts](architecture.md)
- [Docker startup and demo credentials](docker-deployment.md)
- [Project status and next increments](implementation-roadmap.md)
- Swagger UIs: `http://localhost:8081/swagger-ui/index.html` through `http://localhost:8085/swagger-ui/index.html`
