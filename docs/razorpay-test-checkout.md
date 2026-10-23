# Razorpay test checkout

## What the checkout does

The browser sends only book IDs and quantities. The Order service validates the signed-in customer's server-side cart, asks Catalog for current prices and available stock, then stores a pending order using a stable idempotency key. The Payment service creates one Razorpay order for that internal order and records separate payment attempts. Repeating a request with the same attempt key returns the same attempt. A new attempt is allowed only after the prior attempt is confirmed failed or cancelled.

The React payment page displays UPI only and opens Razorpay Standard Checkout. The browser callback is treated as a verification request, not as proof of payment. Payment service checks the HMAC signature, fetches the payment from Razorpay, and verifies the gateway order, amount, and INR currency. The signed webhook uses the raw request bytes, and event IDs are stored uniquely to make redelivery safe. Only verified captured payment publishes the success event that lets Order transition to `PAID` and enqueue fulfilment once.

Razorpay's official checkout controls the available UPI intent/QR behavior. MSRBookNest does not build UPI deep links or ask for a UPI PIN or bank password. Which UPI options appear depends on the account, device, browser, and provider configuration.

## Configure local test mode

1. Create Razorpay Test Mode API keys in the Razorpay dashboard.
2. Copy `.env.example` to `.env`, then fill in the test `key_id`, `key_secret`, and a separately generated webhook secret. Keep `RAZORPAY_ALLOW_LIVE=false`.
3. Start the stack with `docker compose up -d --build`.
4. For webhooks, expose local port 8085 through a trusted HTTPS development tunnel and configure the Razorpay webhook URL as `https://<your-test-tunnel>/api/payments/webhook`. Subscribe to `payment.captured`, `payment.failed`, and `order.paid`; use the same webhook secret in `.env`. Without a reachable webhook URL, authenticated status checks still reconcile payments with Razorpay.
5. Open `http://localhost:5173`, sign in, add a book, and continue through checkout.

When test credentials are missing, invalid, or not test keys, payment creation stays disabled. The public GitHub Pages demo uses a browser-only adapter: it can create an idempotent sample order and lets you simulate success or failure to demonstrate the UI. That simulation never opens Razorpay, contacts a payment provider, or charges money. Use the local Docker stack above for the real Razorpay Test Mode flow.

## API flow

All customer endpoints below except the webhook require `Authorization: Bearer <JWT>`.

### Ask for the backend quote

```http
POST /api/orders/quote
Content-Type: application/json

{"items":[{"bookId":1,"quantity":2}]}
```

The endpoint compares the submitted IDs and quantities with the authenticated cart and returns current item prices, subtotal, discount, delivery charge, final amount, and currency.

### Create or replay the pending order

```http
POST /api/orders
Authorization: Bearer <JWT>
Idempotency-Key: <stable-checkout-uuid>
Content-Type: application/json

{
  "orderRequestId":"<same-stable-checkout-uuid>",
  "customerName":"Demo Reader",
  "phone":"9000000001",
  "address":"12 Example Road, Bengaluru 560001",
  "paymentMethod":"UPI",
  "items":[{"bookId":1,"quantity":2}]
}
```

The backend ignores client prices, reserves stock, and fingerprints the checkout details. A retry with the same key and same details returns the same order; reusing a key with changed details is rejected.

### Create or replay a provider checkout attempt

```http
POST /api/payments/create
Authorization: Bearer <JWT>
Idempotency-Key: <payment-attempt-uuid>
Content-Type: application/json

{"orderRequestId":"<stable-checkout-uuid>","attemptRequestId":"<payment-attempt-uuid>"}
```

The response contains the public Razorpay key ID, provider order ID, attempt ID, amount, currency, and status. It never contains the key secret.

### Verify the checkout callback

```http
POST /api/payments/verify
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "orderRequestId":"<stable-checkout-uuid>",
  "attemptRequestId":"<payment-attempt-uuid>",
  "razorpayOrderId":"order_...",
  "razorpayPaymentId":"pay_...",
  "razorpaySignature":"<checkout-signature>"
}
```

The backend verifies the signature against the provider order ID stored in MySQL and fetches the payment from Razorpay before returning `SUCCESS`.

### Reconcile status

```http
GET /api/payments/orders/<stable-checkout-uuid>/status
Authorization: Bearer <JWT>
```

On modal dismissal, the UI stays pending until this endpoint checks the gateway order's payment list. A new attempt is created only if Razorpay confirms that the previous attempt failed or no payment completed.

### Webhook

```http
POST /api/payments/webhook
X-Razorpay-Signature: <signature>
X-Razorpay-Event-Id: <event-id>
Content-Type: application/json

<raw Razorpay event body>
```

The controller forwards the untouched body bytes for HMAC verification. Duplicate event IDs are acknowledged without repeating fulfilment.

## Test scenarios

- **Success:** use Razorpay Test Mode's success UPI test flow. The screen remains “Verifying your payment” until the backend confirms captured status.
- **Failure:** use Razorpay Test Mode's failure UPI test flow. The order stays unpaid; after the provider confirms failure, Retry Payment creates a new attempt under the existing order.
- **Customer closes checkout:** the UI checks the provider order. It shows Cancelled only after the server confirms no payment completed; otherwise it remains Processing.
- **Delayed outcome:** use Check payment status. Pending stays pending until the API or webhook establishes the state.
- **Duplicate click/network retry:** retry the same request with the same idempotency key. The same order/attempt is returned.
- **Duplicate or delayed webhook:** send the same signed event again; event ID uniqueness makes replay safe, and a failure event cannot downgrade a verified success.
- **Late capture after cancellation:** the order remains cancelled, its reconciliation state is marked `REFUND_REQUIRED`, and fulfillment is not enqueued. Complete the refund through the provider dashboard and reconcile it manually; automated refunds are not part of this demo.
- **Invalid signature or altered amount:** backend rejects signature, order, amount, or currency mismatches. Unit tests cover signature matching and payment-attempt idempotency/state transitions.

Razorpay Test Mode is a provider sandbox; it does not transfer funds. The integration is not enabled for live money movement. Production requires merchant onboarding/approval, production API/webhook configuration, a public HTTPS deployment, monitored webhook delivery, a real refund/reconciliation process, and a production secret store.
