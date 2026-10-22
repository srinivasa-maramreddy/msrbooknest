# Run and present msrbooknest

## Start the complete stack

Install Docker Desktop and run these commands from the repository root:

```bash
docker compose up -d --build
docker compose ps
```

The first startup builds the React storefront and six Spring Boot services, then starts MySQL and Kafka. Wait until the Spring containers finish starting before signing in.

| Component | URL |
|---|---|
| Storefront | [http://localhost:5173](http://localhost:5173) |
| Auth API | [Swagger UI](http://localhost:8081/swagger-ui/index.html) · [health](http://localhost:8081/actuator/health) |
| Catalog API | [Swagger UI](http://localhost:8082/swagger-ui/index.html) · [health](http://localhost:8082/actuator/health) |
| Cart API | [Swagger UI](http://localhost:8084/swagger-ui/index.html) · [health](http://localhost:8084/actuator/health) |
| Order API | [Swagger UI](http://localhost:8083/swagger-ui/index.html) · [health](http://localhost:8083/actuator/health) |
| Payment API | [Swagger UI](http://localhost:8085/swagger-ui/index.html) · [health](http://localhost:8085/actuator/health) |
| Buyback API | [Swagger UI](http://localhost:8086/swagger-ui/index.html) · [health](http://localhost:8086/actuator/health) |
| Kafka host listener | `localhost:9092` |

The `/actuator/health/liveness` and `/actuator/health/readiness` paths are also available on each Spring service. Only health endpoints are exposed by Actuator.

## Sign in and run the demo

Seeded reader account:

- Email: `reader@msrbooknest.demo`
- Password: `ReaderDemo@123`
- Mobile login: `9000000001` with the same password

Seeded admin account (catalog/order admin APIs):

- Email: `admin@msrbooknest.demo`
- Password: `AdminDemo@123`

The UI lets you register with staged mobile and email OTP verification. In this local Compose configuration, codes are displayed in the UI for the demo; SMS and email are not sent. Passwords must be at least 10 characters and include a special character.

## Used-book buyback walkthrough

Open [the buyback dashboard](http://localhost:5173/#buyback) and sign in as the reader. Submit a title, author, condition, notes, and up to four book photos. Use a second browser profile or sign out and sign in as the seeded admin to work the queue. Admin actions simulate a video review, create a versioned quotation, mark a DTDC shipment as received, and record inspection evidence. Return to the reader account to accept or decline the offer, enter mock tracking, or choose a return, revised quote, or dispute. The admin dashboard can dispatch and deliver a mock return or record a final dispute decision.

If inspection passes, the service writes exactly one mock payout record for the accepted quote. If inspection fails, the default return label is ₹120 paid by MSR Booknest; a dispute decision can assign that demo fee to the customer. The inbound DTDC shipment is always paid by the customer. None of the video, courier, payout, or NPCI paths contacts a real provider. See [the buyback workflow details](used-book-buyback.md).

### 3-minute checkout walkthrough

1. Sign in as the reader and add a couple of books to the bag.
2. Open checkout and use **UPI** with `success@upi`.
3. Show the order moving from `PENDING_PAYMENT` to `PAID` while Order, Payment, Kafka, and Catalog coordinate asynchronously.
4. Place another order with `fail@upi`; show `PAYMENT_FAILED` and explain the inventory release event.
5. Open the Swagger UIs or health endpoints to show independently running service APIs.

No actual NPCI or card network is contacted and no money moves. Each simulator response returns a clearly marked demo reference.

## Inspect activity

```bash
docker compose logs -f order-service payment-service catalog-service
docker compose ps
```

Kafka topic names and event ownership are listed in [architecture.md](architecture.md). The order outbox records are persisted with order changes so an unavailable broker does not erase the event that needs to be published.

## Stop the app

```bash
docker compose down
```

This stops containers and keeps the named MySQL and Kafka volumes. `docker compose down -v` also deletes saved local data.

## Optional email/SMS delivery

For the interviewer demo, keep the local OTP display enabled. To try real registration delivery, copy [.env.otp.example](../.env.otp.example) to the ignored `.env.otp` file and add valid SMTP and MSG91 credentials. Then run:

```bash
docker compose --env-file .env.otp -f docker-compose.yml -f docker-compose.otp.yml up -d --build
```

The override enables both OTP providers and hides development codes. Compose passes no email/SMS credentials to Auth in the default demo stack. Never commit provider credentials. If a provider is missing or fails, registration returns a delivery error and OTPs are never written to logs. To return to demo behavior, run `docker compose up -d` with the base Compose file so all services share the base configuration again.

## Deployment boundary

The Compose database password, JWT signing key fallback, demo accounts, local origins, one-node Kafka setup, and simulated payment path are intended for localhost only. Before a public deployment, use managed secrets and key rotation, least-privilege service database accounts, database migrations, restricted origins, a production identity strategy, resilient Kafka settings, monitoring, and a certified payment provider.
