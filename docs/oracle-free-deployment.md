# Free hosted Spring backend demo

This setup runs the existing six Spring Boot services, MySQL, and Kafka on one Oracle Cloud Infrastructure (OCI) Always Free Arm VM. Nginx routes API paths internally, and Cloudflare Quick Tunnel provides a temporary HTTPS URL for the GitHub Pages frontend. No application ports need to be opened to the internet. The Pages checkout calls the Payment service's mock NPCI endpoint, recording success or failure in MySQL and publishing approved payments to Kafka without contacting a bank.

This is a portfolio/demo setup, not production hosting. OCI currently lists an Always Free Ampere allowance equivalent to 2 OCPUs and 12 GB RAM in the tenancy's home region. Capacity can be unavailable, and OCI may reclaim compute instances it considers idle. Cloudflare Quick Tunnels are temporary, use a random URL, and have no uptime guarantee; their documentation limits them to testing and development. See [OCI Always Free resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) and [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).

## 1. Create the free VM

In OCI, create an Always Free eligible `VM.Standard.A1.Flex` instance in the tenancy's home region. Allocate up to 2 OCPUs and 12 GB RAM, use Ubuntu or Oracle Linux, and attach an SSH key. Use the default 50 GB boot volume. If the console says the instance is not Always Free eligible or displays an estimated charge, stop before creating it. OCI may show an out-of-capacity error; retry later if needed.

Allow SSH (TCP 22) only from your current IP address. The API is published through an outbound tunnel, so do not open ports 8081–8086, 3306, 9092, 80, or 443 on the VM's public network rules.

Install Git, Docker Engine, and the Docker Compose plugin using [Docker's official installation guide](https://docs.docker.com/engine/install/) for the selected Linux image. Then connect by SSH and clone this repository:

```bash
git clone https://github.com/srinivasa-maramreddy/msrbooknest.git
cd msrbooknest
```

## 2. Configure local-only server secrets

Create a protected `.env` file on the VM. These values stay on the VM and must not be committed or pasted into GitHub:

```bash
umask 077
printf 'APP_JWT_SECRET=%s\nMYSQL_ROOT_PASSWORD=%s\nAPP_WEB_ORIGINS=https://srinivasa-maramreddy.github.io\n' \
  "$(openssl rand -base64 48 | tr -d '\n')" \
  "$(openssl rand -hex 32)" > .env
```

The hosted demo intentionally keeps the sample customer/admin accounts and displays registration OTP codes for demonstration. Razorpay live payments are forced off. Do not use personal customer information or real payment data in this demo.

## 3. Build and start the backend

Build one service image at a time to fit the free VM's CPU, then start the services:

```bash
docker compose --parallel 1 -f docker-compose.yml -f docker-compose.oracle-free.yml build
docker compose -f docker-compose.yml -f docker-compose.oracle-free.yml up -d
```

Check startup and get the Cloudflare HTTPS URL printed by the tunnel container:

```bash
docker compose -f docker-compose.yml -f docker-compose.oracle-free.yml ps
docker compose -f docker-compose.yml -f docker-compose.oracle-free.yml logs cloudflare-tunnel
```

Copy the `https://<random-name>.trycloudflare.com` URL from the log. Confirm its root responds with `msrbooknest-api-gateway` and `status: ok`.

## 4. Connect GitHub Pages to the backend

The GitHub Pages workflow defaults to the Render API gateway. To point the site at a different public gateway, open **srinivasa-maramreddy/msrbooknest → Settings → Secrets and variables → Actions → Variables**, then create a repository variable named `VITE_BACKEND_BASE_URL` with the copied HTTPS URL (no trailing slash). This value is public frontend configuration, not a password.

Open **Actions → Deploy msrbooknest to GitHub Pages → Run workflow** to rebuild the site against the configured Spring API. The Pages build no longer falls back to browser-only authentication.

The API gateway URL changes when the Quick Tunnel is recreated. If that happens, copy the new URL, update `VITE_BACKEND_BASE_URL`, and rerun the Pages workflow. For a stable hostname, use a named Cloudflare Tunnel and a domain you control; a domain may have a registration cost.

## 5. Operations

```bash
# Check services and health
docker compose -f docker-compose.yml -f docker-compose.oracle-free.yml ps
docker compose -f docker-compose.yml -f docker-compose.oracle-free.yml logs -f auth-service catalog-service order-service

# Update the app from GitHub
git pull --ff-only
docker compose --parallel 1 -f docker-compose.yml -f docker-compose.oracle-free.yml build
docker compose -f docker-compose.yml -f docker-compose.oracle-free.yml up -d
```

The named MySQL and Kafka volumes keep their data across container rebuilds, but this guide does not configure off-VM backups. The quick tunnel is for demo access; it has no SLA, and its hostname is not guaranteed to remain the same after tunnel restarts.
