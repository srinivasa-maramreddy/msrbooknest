# msrbooknest React storefront

The storefront uses the local Spring Boot auth API for demo registration and login. It saves a signed demo JWT in browser local storage and sends it to the protected cart and order APIs.

Run the full demo with Docker Compose from the repository root. The stack starts the storefront, all five Spring Boot services, MySQL, and Kafka. The storefront is available at `http://localhost:5173`.

The login dialog shows the demo reader account. Checkout requires Razorpay Test Mode credentials in the backend `.env`; the UI offers UPI through Razorpay Standard Checkout. Payment callbacks are verified by the Payment service before an order can become paid. Checkout remains disabled when credentials are absent, and the public GitHub Pages mock storefront does not claim to process payments. See [the test checkout guide](../docs/razorpay-test-checkout.md).
