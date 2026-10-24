# No-card demo deployment

This setup keeps the React site on GitHub Pages and deploys the six Spring Boot services plus an Nginx API gateway as Render free web services. Aiven supplies the free MySQL and Kafka services. Email verification uses Gmail's HTTPS API, which works with Render's free outbound networking. The Render blueprint is in [`render.yaml`](../render.yaml).

This is for a portfolio demo, not production. Render free services sleep after 15 minutes without requests and can take about a minute to wake. The 750 free instance-hours are shared across the workspace, so seven services that ran continuously would exceed the monthly allowance. Aiven free services have small capacity limits and may power off after extended inactivity. They can be powered back on in the Aiven dashboard. See [Render free limits](https://render.com/docs/free), [Aiven MySQL free tier](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier), and [Aiven Kafka free tier](https://aiven.io/docs/products/kafka/free-tier/kafka-free-tier).

## 1. Create the Aiven services

Create a free Aiven account and add one free MySQL service and one free Kafka service in the same nearby region. Choose the `$0` free service plan; don't start a paid trial. Aiven says these free services do not require a payment card.

From each Aiven service's connection page, keep these values handy:

- MySQL hostname, port, username, and password.
- Kafka bootstrap server (`hostname:port`), username, and password.

The Render configuration uses TLS for MySQL and SASL over TLS for Kafka. The free Kafka tier allows up to five topics with two partitions each. The bookstore uses those five topics for checkout; the hosted configuration disables the optional buyback Kafka event so it stays within the free topic limit. Buyback records and their timeline continue to be stored in MySQL.

## 2. Create the Render deployment

1. Sign in to [Render](https://dashboard.render.com/) and connect the GitHub account that can access `srinivasa-maramreddy/msrbooknest`.
2. Choose **New → Blueprint**, select the `msrbooknest` repository and `main` branch, then let Render read `render.yaml`.
3. During setup, Render asks for the Aiven values marked as secrets. Enter the MySQL values on the auth service and the Kafka values on the catalog service. Render shares those values with the services that need them. Never put them in this repository or send them in chat.
4. Create all seven services on the `Free` plan. Wait for the auth and catalog services to receive their Aiven settings before expecting the other services to become healthy.

The API gateway will normally be available at:

```text
https://msrbooknest-srinivas-api.onrender.com
```

Use the actual URL shown by Render if the name was changed or already taken. Opening the gateway root should return `msrbooknest-api-gateway` with status `ok`.

If the services report a database error, confirm the Aiven MySQL service is powered on and that it permits database creation for its configured user. The application creates `auth_db`, `catalog_db`, `cart_db`, `order_db`, `payment_db`, and `buyback_db` on first connection. If the Aiven user cannot create databases, create those six databases in Aiven's SQL console and redeploy.

## 3. Configure real email OTP

The Auth service sends verification and password-reset codes through the Gmail API over HTTPS. It does not use SMS, a demo code, or Gmail SMTP. Render's Free web services block SMTP ports, so do not configure ports 25, 465, or 587.

1. In Google Cloud Console, create/select a project and enable the Gmail API.
2. Create an OAuth 2.0 client and add `https://developers.google.com/oauthplayground` as an authorized redirect URI.
3. Open [Google OAuth 2.0 Playground](https://developers.google.com/oauthplayground), open its settings, and enable **Use your own OAuth credentials**. Enter that OAuth client's ID and secret.
4. In Step 1, authorize the scope `https://www.googleapis.com/auth/gmail.send` using the Gmail account that will send MSRBookNest messages. In Step 2, exchange the authorization code and copy the refresh token. Never paste these credentials into chat or commit them.
5. In Render, open the Auth service's **Environment** page and add these variables:

   - `GMAIL_SENDER_EMAIL`: the same Gmail account you authorized
   - `GMAIL_OAUTH_CLIENT_ID`: OAuth client ID
   - `GMAIL_OAUTH_CLIENT_SECRET`: OAuth client secret
   - `GMAIL_OAUTH_REFRESH_TOKEN`: refresh token
   - `APP_EMAIL_OTP_ENABLED`: `true`

   The code uses the Gmail API scope `gmail.send` and refreshes short-lived access tokens server-side. Save and deploy the service after entering the values. The refresh token and client secret must remain private.

For quick testing, set your Google OAuth consent screen to **Testing** and add the sender account as a test user. Google expires test-user authorizations after seven days. For a public service that should keep sending continuously, publish the OAuth app and complete Google's verification for the sensitive `gmail.send` scope.

Google's Gmail API has per-user sending quotas and Google account sending limits. This is suitable for a small portfolio demo, not high-volume transactional email. For higher volume, use a transactional provider with a verified sending domain.

## 4. Connect GitHub Pages

The GitHub Pages workflow uses the API gateway URL by default. To override it, open **srinivasa-maramreddy/msrbooknest → Settings → Secrets and variables → Actions → Variables** and set:

```text
Name:  VITE_BACKEND_BASE_URL
Value: https://msrbooknest-srinivas-api.onrender.com
```

Use the gateway's actual Render URL and omit a trailing slash. The Pages workflow automatically rebuilds after a storefront change on `main`; you can also run it from **Actions → Deploy msrbooknest to GitHub Pages → Run workflow**. The site uses the Spring API rather than browser demo authentication. The API gateway's root and individual service `/actuator/health` paths can be used to inspect service availability.

## Demo behavior and limits

- Email OTP and password recovery require Gmail OAuth variables on the Auth service. Never enable a fake OTP response in a public deployment. SMS OTP is not used.
- Razorpay live payments remain disabled. Checkout uses the mock NPCI simulator.
- Free Render services sleep while idle, and Render may suspend services if the shared free-hour or usage limits are reached.
- Aiven's free MySQL has 1 GB of storage. Its free Kafka tier has five topics, two partitions per topic, capped throughput, and limited retention.
- The Aiven free tier can power off services after long inactivity. Start them in Aiven before the bookstore demo.
- Public Render URLs are used for the API gateway and service-to-service calls because Render free web services don't accept private-network traffic. Use this setup for sample data only; secure admin accounts and payment systems before any real customer use.
