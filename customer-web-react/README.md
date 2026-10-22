# msrbooknest React storefront

The storefront uses the local Spring Boot auth API for demo registration and login. It saves a signed demo JWT in browser local storage and sends it to the protected cart and order APIs.

Run the full demo with Docker Compose from the repository root. The stack starts the storefront, all five Spring Boot services, MySQL, and Kafka. The storefront is available at `http://localhost:5173`.

The login dialog shows the demo reader account. Checkout uses a local NPCI-style UPI simulator: `success@upi` succeeds and `fail@upi` simulates a decline. No real payment is initiated.
