# Used-book buyback demo

## Start and open

The buyback UI is part of the React storefront:

- [Buyback dashboard](http://localhost:5173/#buyback)
- Buyback API health: [http://localhost:8086/actuator/health](http://localhost:8086/actuator/health)
- Buyback API docs: [http://localhost:8086/swagger-ui/index.html](http://localhost:8086/swagger-ui/index.html)

Start the root Compose stack with `docker compose up -d --build`. Sign in with the seeded reader or admin account documented in [docker-deployment.md](docker-deployment.md). Use a second browser profile to keep reader and admin sessions open side by side.

## Customer flow

1. Add a book title, author, optional ISBN, condition, notes, and up to four JPG/PNG/WebP photos (500 KB each).
2. The admin completes a clearly marked simulated video review and creates a quotation with a reason.
3. Review the quotation version and explicitly accept or decline it. The accepted version is locked; later offers are new versions.
4. After acceptance, send the book to the demo store address using DTDC, at the customer's expense, and enter a mock tracking number.
5. The admin marks receipt and records the physical inspection result and evidence photos.
6. A passing inspection completes one mock payout at the accepted amount. A failed inspection exposes the evidence and offers return, revised quotation, or dispute choices.
7. A return shows the ₹120 mock return label and payer, then records mock DTDC dispatch and delivery. An admin records a final dispute decision with rationale.

## State model

```text
SUBMITTED → UNDER_REVIEW → QUOTED → AWAITING_SHIPMENT → IN_TRANSIT → RECEIVED
                                                       └──────────────┬────────
                                                              pass → PAYMENT_COMPLETED
                                                              fail → INSPECTION_FAILED
                                                                      ├→ RETURN_REQUESTED → RETURN_DISPATCHED → RETURN_DELIVERED
                                                                      ├→ REVISION_REQUESTED → REVISED_QUOTE_PENDING
                                                                      └→ DISPUTE_OPEN → final admin decision
```

The admin's initial quote action is allowed only after mock video review. Customer decisions include the quote version; a stale version is rejected. Accepted amounts and reasons are retained in quote history. A unique database constraint on the mock payout's buyback ID prevents duplicate payout rows, and case updates use a database write lock.

## Demo policy and data

- Inbound DTDC shipping is always paid by the customer.
- For a failed physical inspection, the default mock return fee is ₹120 paid by MSR Booknest. An admin dispute decision can explicitly assign the same displayed fee to the customer.
- Photos, notes, activity entries, quotation versions, inspection evidence, tracking IDs, and mock payment records persist in the local `buyback_db` schema.
- `buyback.status-events` carries status-change events to Kafka.
- No camera/video, courier, NPCI, bank, or payment provider is contacted. The address, tracking IDs, and payout references are demo values.

## REST workflow endpoints

All case endpoints require the existing msrbooknest JWT. Customer requests are scoped to the token subject; admin review endpoints require the `ADMIN` role.

| Action | Endpoint |
|---|---|
| List own requests / admin queue | `GET /api/buybacks` |
| Submit a book | `POST /api/buybacks` |
| Complete simulated video review | `POST /api/buybacks/{id}/review` |
| Offer initial quote / customer decision | `POST /api/buybacks/{id}/quote`, `POST /api/buybacks/{id}/quote/decision` |
| Enter inbound tracking / mark received | `POST /api/buybacks/{id}/shipment`, `POST /api/buybacks/{id}/receive` |
| Record physical inspection | `POST /api/buybacks/{id}/inspection` |
| Choose return, revision, dispute | `POST /api/buybacks/{id}/failure-choice` |
| Offer/accept a revision | `POST /api/buybacks/{id}/revision`, `POST /api/buybacks/{id}/revision/decision` |
| Dispatch/deliver return | `POST /api/buybacks/{id}/return/dispatch`, `POST /api/buybacks/{id}/return/deliver` |
| Resolve dispute | `POST /api/buybacks/{id}/dispute/resolve` |
