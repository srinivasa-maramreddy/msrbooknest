# Deploy MSRBookNest to Lightsail

This deployment keeps the React storefront on GitHub Pages and runs the six Spring Boot services, MySQL, Kafka, and an HTTPS API gateway on one Lightsail instance. It is sized for the $44/month Linux bundle (2 vCPUs, 8 GB RAM, 160 GB SSD). AWS prices and network transfer allowances vary by region; confirm the selected bundle in the Lightsail console.

## 1. Create the Lightsail server

In Lightsail, choose:

- Region: Singapore, to match the current backend region.
- Platform: Linux/Unix.
- Bundle: 8 GB RAM, 2 vCPUs, 160 GB SSD (listed at $44/month).
- Operating system: Ubuntu 24.04 LTS.

After it is running, attach a static IPv4 address. In Networking, allow inbound TCP 22 only from your IP, and TCP 80 and 443 from all addresses. Do not open ports 3306, 9092, or the Spring service ports publicly.

## 2. Point an API hostname at the server

Create an `A` record such as `api.your-domain.example` pointing to the Lightsail static IPv4 address. HTTPS from the GitHub Pages site requires a DNS name; Caddy will request and renew the TLS certificate automatically once DNS resolves to the server and ports 80 and 443 are reachable.

## 3. Install Docker and check out the repository

Connect to Ubuntu over SSH and install Docker Engine with the official Docker Ubuntu instructions. Then clone the repository and enter its directory:

```bash
git clone https://github.com/srinivasa-maramreddy/msrbooknest.git
cd msrbooknest
```

## 4. Set deployment secrets

Copy `.env.lightsail.example` to `.env.lightsail`. Set `API_DOMAIN` to the DNS name from step 2, and replace the JWT and MySQL passwords with long, unique random values. Keep this file on the server only; it is ignored by Git.

To enable real email OTP, set `APP_EMAIL_OTP_ENABLED=true` and fill in the Gmail sender address and OAuth client ID, client secret, and refresh token. Otherwise leave OTP disabled until those values are ready. Payment stays in mock mode; this compose file does not enable live Razorpay payments.

## 5. Start the stack

Run from the repository root:

```bash
docker compose --parallel 1 --env-file .env.lightsail -f docker-compose.yml -f docker-compose.lightsail.yml up -d --build
```

The profile override keeps the React container off. MySQL and Kafka data use persistent Docker volumes. On the first boot, allow several minutes for the Java services to initialize.

Check container status and logs:

```bash
docker compose --env-file .env.lightsail -f docker-compose.yml -f docker-compose.lightsail.yml ps
docker compose --env-file .env.lightsail -f docker-compose.yml -f docker-compose.lightsail.yml logs -f api-gateway auth-service catalog-service
```

When the DNS record is active, verify `https://<API_DOMAIN>/` reports the gateway as healthy and `https://<API_DOMAIN>/health/catalog` reports the catalog health. The other health routes are `/health/auth`, `/health/cart`, `/health/orders`, `/health/payments`, and `/health/buyback`.

## 6. Connect the GitHub Pages storefront

In the GitHub repository, open **Settings → Secrets and variables → Actions → Variables** and set both variables to the HTTPS API hostname:

- `VITE_BACKEND_BASE_URL` = `https://<API_DOMAIN>`
- `VITE_AUTH_API_BASE_URL` = `https://<API_DOMAIN>`

Then run the **Deploy msrbooknest to GitHub Pages** workflow. The Pages build will use the Lightsail API gateway for all six services.

## Capacity and operations

The compose override assigns bounded memory to Java, Kafka, and MySQL for this 8 GB instance. It is intended for the small recruiter demo, not high availability or production traffic. Keep a Lightsail snapshot before major changes and monitor memory and disk usage after the first demo sessions.
