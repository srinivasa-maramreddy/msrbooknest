# Enterprise Bookstore (Production-Ready Module)

This module is a refactored enterprise-grade bookstore backend + frontend using layered architecture, JWT security, Flyway migrations, Swagger, Docker, and tests.

## 1) Run Backend (Mac) - Line by Line

```bash
cd /Users/maramreddysrinivasareddy/Documents/New\ project/bookstore-microservices
```

```bash
docker compose -f docker-compose.enterprise.yml up -d mysql-enterprise
```

```bash
cd /Users/maramreddysrinivasareddy/Documents/New\ project/bookstore-microservices/enterprise-bookstore
```

```bash
mvn spring-boot:run
```

Backend runs at:
- `http://localhost:8090`
- Swagger: `http://localhost:8090/swagger-ui/index.html`

## 2) Run Frontend (Mac) - Line by Line

Open a new terminal:

```bash
cd /Users/maramreddysrinivasareddy/Documents/New\ project/bookstore-microservices/enterprise-bookstore/frontend
```

```bash
python3 -m http.server 5600
```

Open:
- `http://localhost:5600`

## 3) Key REST Endpoints

- `GET /books`
- `GET /books/{id}`
- `POST /books` (ADMIN only)
- `PUT /books/{id}` (ADMIN only)
- `DELETE /books/{id}` (ADMIN only)
- `POST /auth/register`
- `POST /auth/verify-otp`
- `POST /auth/resend-otp`
- `POST /auth/login`
- `POST /orders` (authenticated)
- `GET /orders/me` (authenticated)
- `PATCH /orders/{id}/status?status=SHIPPED` (ADMIN only)

## 4) OTP Verification Flow

Registration OTP flow:

1. `POST /auth/register` with `verificationChannel` set to `EMAIL` or `PHONE`
2. `POST /auth/verify-otp` with `destination`, `otpCode`, and `purpose=REGISTRATION`

Login OTP flow:

1. `POST /auth/login` with `email`, `password`, and `verificationChannel`
2. `POST /auth/verify-otp` with `destination`, `otpCode`, and `purpose=LOGIN`

If OTP expires, call:
- `POST /auth/resend-otp` with `purpose=REGISTRATION` or `purpose=LOGIN`

### Optional real email delivery

By default, OTP is logged in backend logs.  
To send real email OTP, set:

- `APP_OTP_EMAIL_ENABLED=true`
- `APP_OTP_MAIL_FROM=<your-email>`
- `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`

## 5) Admin Registration

Use the admin key from config (`application.yml`):
- default: `admin-secret-key`

Example body:
```json
{
  "fullName": "Owner Admin",
  "email": "owner@bookstore.com",
  "phoneNumber": "9876543210",
  "password": "Pass@1234",
  "role": "ADMIN",
  "verificationChannel": "EMAIL",
  "adminRegistrationKey": "admin-secret-key"
}
```

## 6) Docker Full App

From project root:

```bash
cd /Users/maramreddysrinivasareddy/Documents/New\ project/bookstore-microservices
```

```bash
docker compose -f docker-compose.enterprise.yml up -d
```

## 7) Test and Build

```bash
cd /Users/maramreddysrinivasareddy/Documents/New\ project/bookstore-microservices
```

```bash
mvn -pl enterprise-bookstore test
```

```bash
mvn -pl enterprise-bookstore -DskipTests package
```

## 8) Postman

Import these files into Postman:
- `/Users/maramreddysrinivasareddy/Documents/New project/bookstore-microservices/enterprise-bookstore/postman/Enterprise-Bookstore-OTP.postman_collection.json`
- `/Users/maramreddysrinivasareddy/Documents/New project/bookstore-microservices/enterprise-bookstore/postman/Enterprise-Bookstore-OTP.postman_environment.json`

OTP variable notes:
- Set `otpCode` for customer OTP verify request.
- Set `loginVerificationChannel` (`EMAIL` or `PHONE`) for login OTP send request.
- Set `adminOtpCode` for admin OTP verify request.
