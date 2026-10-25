# Deploy MSRBookNest to Lightsail

This guide matches the current two-instance Mumbai setup. The React storefront remains on GitHub Pages. The backend runs in Docker across two Lightsail instances and connects to one Lightsail managed MySQL database.

## Current layout

| Resource | Placement | Services |
| --- | --- | --- |
| `msrbooknest-node-1` | Mumbai, 2 GB RAM | API gateway, HTTPS proxy, auth, catalog, cart |
| `msrbooknest-node-2` | Mumbai, 2 GB RAM | Kafka, order, payment, buyback |
| Lightsail managed MySQL | Mumbai | Six service schemas: `auth_db`, `catalog_db`, `cart_db`, `order_db`, `payment_db`, `buyback_db` |

The repository has a separate Compose file for each instance: `docker-compose.lightsail.node1.yml` and `docker-compose.lightsail.node2.yml`. Both use `.env.lightsail` for the shared configuration. Do not use the local-demo `docker-compose.yml` or the old single-server `docker-compose.lightsail.yml` for this two-server setup; those start their own MySQL and Kafka containers.

The only managed database is `Database-1` in Mumbai. It is now **Available**: MySQL 8.4.11, 1 GB RAM, 2 vCPUs, and 40 GB SSD. Public access is disabled, so only Lightsail resources in the same region can connect. The local ignored `.env.lightsail` has its endpoint, port, and master username filled in; set its database password before copying it to either server. The Compose configuration points all six services at that shared database and requires TLS (`MYSQL_SSL_MODE=REQUIRED`). Do not create another database.

## 1. Prepare networking

Attach a static IPv4 address to node 1 and point an API hostname, such as `api.your-domain.example`, at it. The HTTPS proxy on node 1 obtains a certificate automatically after DNS resolves and inbound ports 80 and 443 are open.

In the Lightsail networking firewall, allow:

- Node 1: TCP 80 and 443 from the internet; TCP 22 only from your admin IP; TCP 8082 and 8084 from node 2's private address.
- Node 2: TCP 22 only from your admin IP; TCP 8083, 8085, 8086, and 9092 from node 1's private address.

Keep database port 3306 and all service/Kafka ports closed to the public internet. Configure the managed database for private Lightsail connectivity if available, and keep it in Mumbai. Do not publish node 1's port 8080 publicly; Caddy forwards HTTPS traffic to it inside Docker.

## 2. Install Docker on both instances

Install Docker Engine and the Docker Compose plugin on each Ubuntu instance. Clone the same repository on both:

```bash
git clone https://github.com/srinivasa-maramreddy/msrbooknest.git
cd msrbooknest
```

## 3. Configure shared settings

On each instance, copy `.env.lightsail.split.example` to `.env.lightsail` and edit the copy. Set the two private IP values to the current addresses shown on the Lightsail instance details pages. Use the same values in both files:

```bash
cp .env.lightsail.split.example .env.lightsail
```

Update `API_DOMAIN` to the hostname from step 1. After `Database-1` is **Available**, replace the MySQL placeholders with its connection details. Generate a long random `APP_JWT_SECRET`; do not reuse the sample text or commit the filled `.env.lightsail` file.

Leave email OTP disabled unless the Gmail OAuth sender settings are fully configured. Payments stay in mock mode. The checked-in environment example contains no passwords or OAuth secrets.

## 4. Start node 2 first

On `msrbooknest-node-2`, start Kafka and its three services:

```bash
docker compose --parallel 1 --env-file .env.lightsail -f docker-compose.lightsail.node2.yml up -d --build
docker compose --env-file .env.lightsail -f docker-compose.lightsail.node2.yml ps
```

Kafka advertises its private listener to the three containers on node 2 and its private IP listener to catalog on node 1. No public Kafka endpoint is configured.

## 5. Start node 1

On `msrbooknest-node-1`, start auth, catalog, cart, the API gateway, and HTTPS proxy:

```bash
docker compose --parallel 1 --env-file .env.lightsail -f docker-compose.lightsail.node1.yml up -d --build
docker compose --env-file .env.lightsail -f docker-compose.lightsail.node1.yml ps
```

The gateway sends auth, catalog, and cart traffic to containers on node 1 and sends orders, payments, and buybacks to node 2 over its private IP. Order service calls catalog and cart back over node 1's private IP.

## 6. Check health and connect GitHub Pages

After DNS and TLS are ready, check `https://<API_DOMAIN>/` and these health paths:

`/health/auth`, `/health/catalog`, `/health/cart`, `/health/orders`, `/health/payments`, and `/health/buyback`.

Set these GitHub Actions repository variables to the same HTTPS API origin, then rerun the **Deploy msrbooknest to GitHub Pages** workflow:

- `VITE_BACKEND_BASE_URL` = `https://<API_DOMAIN>`
- `VITE_AUTH_API_BASE_URL` = `https://<API_DOMAIN>`

## Capacity note

These are 2 GB instances. The Compose files set small JVM and Kafka memory limits for a recruiter demo. Build and start the services sequentially as shown, then monitor memory and container restarts during startup. This is a low-cost demo layout, not a highly available production design; either instance or Kafka going down can interrupt application functions.
