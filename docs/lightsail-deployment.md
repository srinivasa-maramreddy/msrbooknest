# Deploy MSRBookNest to Lightsail

This guide matches the current two-instance Mumbai setup. The React storefront remains on GitHub Pages. The backend runs in Docker across two Lightsail instances and connects to one Lightsail managed MySQL database.

## Live deployment status — October 26, 2026

- Storefront: <https://srinivasa-maramreddy.github.io/msrbooknest/>
- API: <https://65-1-0-234.sslip.io/>. Caddy has a valid HTTPS certificate.
- All six public health routes return `UP`: `/health/auth`, `/health/catalog`, `/health/cart`, `/health/orders`, `/health/payments`, and `/health/buyback`.
- The GitHub Pages workflow defaults to the Lightsail API and its latest deployment completed successfully.
- Signup and password-reset email OTP are currently disabled until Gmail OAuth sender settings are configured on the Lightsail auth service. Payments remain simulated.

## Current layout

| Resource | Placement | Services |
| --- | --- | --- |
| `msrbooknest-node-1` | Mumbai, 2 GB RAM | API gateway, HTTPS proxy, auth, catalog, cart |
| `msrbooknest-node-2` | Mumbai, 2 GB RAM | Kafka, order, payment, buyback |
| Lightsail managed MySQL | Mumbai | Six service schemas: `auth_db`, `catalog_db`, `cart_db`, `order_db`, `payment_db`, `buyback_db` |

The repository has a separate Compose file for each instance: `docker-compose.lightsail.node1.yml` and `docker-compose.lightsail.node2.yml`. Both use `.env.lightsail` for the shared configuration. Do not use the local-demo `docker-compose.yml` or the old single-server `docker-compose.lightsail.yml` for this two-server setup; those start their own MySQL and Kafka containers.

The only managed database is `Database-1` in Mumbai: MySQL 8.4.11, 1 GB RAM, 2 vCPUs, and 40 GB SSD. It is available with public access disabled, so only Lightsail resources in the same region can connect. Both nodes have a mode-600 `.env.lightsail` file with matching database and JWT settings. The database password and JWT key are not in Git. All six services use this database with TLS (`MYSQL_SSL_MODE=REQUIRED`). Do not create another database.

## 1. Prepare networking

Node 1 has the attached static IPv4 address `65.1.0.234`. The demo uses `65-1-0-234.sslip.io` as its API hostname, so a separate DNS record is not needed. Caddy has obtained and served a valid certificate over inbound ports 80 and 443.

In the Lightsail instance firewall, allow public web traffic to node 1 on TCP 80 and 443. Limit TCP 22 to your admin IP when possible. Node 2 only needs SSH access for administration. Lightsail resources in the same region can communicate over their private addresses by default, so do not add public firewall rules for the service or Kafka ports.

Keep database port 3306 and all service/Kafka ports closed to the public internet. Database-1 already has public access disabled. Do not publish node 1's port 8080 publicly; Caddy forwards HTTPS traffic to it inside Docker.

## 2. Install Docker on both instances

Install Docker Engine and the Docker Compose plugin on each Ubuntu instance. Clone the same repository on both:

```bash
git clone https://github.com/srinivasa-maramreddy/msrbooknest.git
cd msrbooknest
```

## 3. Configure shared settings

For a replacement instance, copy `.env.lightsail.split.example` to `.env.lightsail` and edit the copy. Set the two private IP values to the current addresses shown on the Lightsail instance details pages. Use matching database and JWT values on both instances:

```bash
cp .env.lightsail.split.example .env.lightsail
```

Set `API_DOMAIN` to `65-1-0-234.sslip.io`. Use Database-1's endpoint, port, master username, and master password. Generate a long random `APP_JWT_SECRET`. Keep the filled `.env.lightsail` file on the servers, set its permissions to `600`, and never commit it.

Leave email OTP disabled unless the Gmail OAuth sender settings are fully configured. Payments stay in mock mode. The checked-in environment example contains no passwords or OAuth secrets.

## 4. Start node 2 first

On `msrbooknest-node-2`, start Kafka and its three services:

```bash
sudo docker compose --parallel 1 --env-file .env.lightsail -f docker-compose.lightsail.node2.yml up -d --build
sudo docker compose --env-file .env.lightsail -f docker-compose.lightsail.node2.yml ps
```

Kafka advertises its private listener to the three containers on node 2 and its private IP listener to catalog on node 1. No public Kafka endpoint is configured.

## 5. Start node 1

On `msrbooknest-node-1`, start auth, catalog, cart, the API gateway, and HTTPS proxy:

```bash
sudo docker compose --parallel 1 --env-file .env.lightsail -f docker-compose.lightsail.node1.yml up -d --build
sudo docker compose --env-file .env.lightsail -f docker-compose.lightsail.node1.yml ps
```

The gateway sends auth, catalog, and cart traffic to containers on node 1 and sends orders, payments, and buybacks to node 2 over its private IP. Order service calls catalog and cart back over node 1's private IP.

## 6. Check health and connect GitHub Pages

Check `https://<API_DOMAIN>/` and these health paths:

`/health/auth`, `/health/catalog`, `/health/cart`, `/health/orders`, `/health/payments`, and `/health/buyback`.

The **Deploy msrbooknest to GitHub Pages** workflow now uses the Lightsail HTTPS API as its default, so repository variables are optional. If you add the `VITE_BACKEND_BASE_URL` or `VITE_AUTH_API_BASE_URL` repository variables later, set both to `https://<API_DOMAIN>`. The storefront is deployed at <https://srinivasa-maramreddy.github.io/msrbooknest/>.

## Capacity note

These are 2 GB instances. Catalog and order use a 192 MB Java heap, 128 MB metaspace cap, and 448 MB container limit after startup memory errors were observed; other Java services and Kafka keep their smaller demo limits. Build and start services sequentially, then monitor memory and container restarts. This is a low-cost demo layout, not a highly available production design; either instance or Kafka going down can interrupt application functions.
