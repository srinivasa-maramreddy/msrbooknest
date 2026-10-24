# msrbooknest interview walkthrough

## 30-second project introduction

“msrbooknest is a React bookstore backed by six Spring Boot services. I used Spring Security and JWT for stateless authentication, Spring Data JPA for service-owned persistence, and Kafka for verified payment settlement and buyback status events. Checkout reserves inventory, uses stable idempotency keys for orders and payment attempts, verifies the Razorpay Test Mode result server-side, and then commits inventory asynchronously. The used-book buyback flow adds explicit quote versions, customer decisions, inspection evidence, and idempotent mock payouts.”

## Live demo in about three minutes

1. Start the stack with `docker compose up -d --build` and run `./scripts/demo-check.sh`.
2. Configure test credentials from [the Razorpay guide](razorpay-test-checkout.md), open `http://localhost:5173`, and sign in as `reader@msrbooknest.demo` / `ReaderDemo@123`.
3. Add two books, review the server quote, and complete a UPI test payment. Point out that the order becomes `PAID` only after Payment verifies the provider callback and status.
4. Retry a declined/cancelled test payment on the same order and show that it gets a new attempt key while preserving the order ID.
5. Open the Order, Payment, and Catalog logs or their Swagger UIs to trace the service boundaries and Kafka handoffs.

### Show used-book buyback

1. Open `http://localhost:5173/#buyback` and sign in as the reader. Submit a book with condition details and photos.
2. Switch to the admin demo account and complete the simulated video review. Enter a quotation and a reason.
3. Return to the reader account, review the numbered quote, accept it, and enter a mock DTDC tracking number.
4. In the admin dashboard, receive the book and pass/fail the simulated inspection. A pass creates one mock payout. A failure reveals the evidence and customer choices for return, revised quote, or dispute.
5. Demonstrate return dispatch/delivery or record a final dispute decision. Point to the activity timeline and the Kafka `buyback.status-events` stream.

Inbound shipping is customer-paid. In the demo policy, failed-inspection returns cost ₹120 and are store-paid unless a dispute decision explicitly assigns the fee to the customer.

Use the registration screen if asked about OTP. The hosted flow sends a one-time code through the Gmail API over HTTPS; local registration can use the `.env.otp` configuration. Codes expire, are hashed in storage, and are never returned to the storefront. Mobile numbers are stored for sign-in, with no SMS OTP integration.

## Explain checkout on a whiteboard

```text
React → Order → Catalog quote + stock reservation
  ↓               ↓
stable key      pending order
  ↓
Payment → Razorpay Test Mode → signature + provider status verification
  ↓
Kafka success → Order state + outbox → Catalog commits stock
```

Mention the three main correctness choices:

- **Outbox:** the order state and the intent to publish are committed together, avoiding the database/Kafka dual-write gap.
- **Idempotency:** the checkout key and request fingerprint prevent duplicate or mutated orders. Payment has one provider order per order and a unique key per attempt; retries reuse the same attempt unless the provider confirms a terminal failure/cancellation.
- **Compensation:** stock is held before payment, committed after verified success, and released after the hold expires if the customer abandons checkout.

Prices are resolved by Catalog, not trusted from browser input. `BigDecimal` and `DECIMAL(12,2)` preserve currency precision across the REST, JPA, and Kafka paths.

## Questions an interviewer may ask

**Why Kafka here?** Razorpay confirmation and inventory settlement cross service boundaries. Kafka lets Order return a pending order while a verified success event updates the order and commits the stock hold asynchronously. The demo also shows why consumers need idempotency and why the UI handles eventual consistency.

**Why the outbox?** Saving an order and publishing to Kafka are two independent writes. The outbox records the event inside the order transaction; a publisher sends it later and retains retry state if Kafka is unavailable.

**What if a message is delivered twice?** The order request ID is unique and payment processing looks up the existing transaction before creating another one. State transitions ignore events for orders that are no longer pending. The production version should also formalize retry limits and dead-letter/replay operations.

**Why not one database transaction across all services?** That would tightly couple service availability and data ownership. The demo uses local synchronous REST for the inventory hold, then Kafka for the payment/result/settlement workflow; the order can be pending while the asynchronous work completes.

**Is this a live payment integration?** No. The browser uses Razorpay Test Mode when test keys are configured. Payment service verifies callback signatures and fetches provider status before publishing success. Live payment is disabled by default.

**What would you harden before production?** Add automated integration tests, migration-managed schemas, least-privilege DB accounts, outbox row locking for multiple replicas, retry/backoff and DLQ/replay, tracing/metrics/alerts, OTP rate limits, managed identity/secrets, and a certified payment integration.

## Useful links

- [Architecture and event contracts](architecture.md)
- [Docker startup and demo credentials](docker-deployment.md)
- [Razorpay test checkout and idempotency](razorpay-test-checkout.md)
- [Project status and next increments](implementation-roadmap.md)
- Swagger UIs: `http://localhost:8081/swagger-ui/index.html` through `http://localhost:8085/swagger-ui/index.html`
