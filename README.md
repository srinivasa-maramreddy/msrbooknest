# Bookstore Microservices (Spring Boot + JWT + MySQL)

## Services
- `auth-service` (port `8081`): register/login and JWT generation
- `catalog-service` (port `8082`): books catalog (public read, admin write)
- `order-service` (port `8083`): place orders and manage status

## Stack
- Java 17
- Spring Boot 3
- Spring Security + JWT
- Spring Data JPA
- MySQL 8

## Start MySQL
```bash
cd /Users/maramreddysrinivasareddy/Documents/New\ project/bookstore-microservices
docker compose up -d
```

## Run each service
Open 3 terminals:

```bash
cd /Users/maramreddysrinivasareddy/Documents/New\ project/bookstore-microservices/auth-service
mvn spring-boot:run
```

```bash
cd /Users/maramreddysrinivasareddy/Documents/New\ project/bookstore-microservices/catalog-service
mvn spring-boot:run
```

```bash
cd /Users/maramreddysrinivasareddy/Documents/New\ project/bookstore-microservices/order-service
mvn spring-boot:run
```

## Run Customer Website
Open a new terminal:

```bash
cd /Users/maramreddysrinivasareddy/Documents/New\ project/bookstore-microservices/customer-web
python3 -m http.server 5500
```

Open:
- `http://localhost:5500`

## Swagger UI
- Auth service: `http://localhost:8081/swagger-ui/index.html`
- Catalog service: `http://localhost:8082/swagger-ui/index.html`
- Order service: `http://localhost:8083/swagger-ui/index.html`

For secured endpoints, click `Authorize` in Swagger and paste:
- `Bearer <your_jwt_token>`

## Auth flow
1. Register user: `POST /api/auth/register`
2. Login user: `POST /api/auth/login`
3. Copy token from response
4. Call protected APIs with:
   - `Authorization: Bearer <token>`

## Demo admin user
- Register with role `ADMIN` using `/api/auth/register`

## Example curl
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Owner","email":"owner@book.com","password":"Pass@123","role":"ADMIN"}'
```

## Enterprise Refactor Module
- Module path: `/Users/maramreddysrinivasareddy/Documents/New project/bookstore-microservices/enterprise-bookstore`
- Backend port: `8090`
- Frontend path: `/Users/maramreddysrinivasareddy/Documents/New project/bookstore-microservices/enterprise-bookstore/frontend`
- Full run guide: `/Users/maramreddysrinivasareddy/Documents/New project/bookstore-microservices/enterprise-bookstore/README.md`

## Postman JSON Files
- `/Users/maramreddysrinivasareddy/Documents/New project/bookstore-microservices/postman/Enterprise-Bookstore-OTP.postman_collection.json`
- `/Users/maramreddysrinivasareddy/Documents/New project/bookstore-microservices/postman/Enterprise-Bookstore-OTP.postman_environment.json`
