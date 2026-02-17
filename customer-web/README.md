# Customer Web UI (BookNest)

A customer-facing bookstore website connected to your Spring Boot microservices.

## Features
- Premium responsive UI (mobile + desktop)
- New Arrivals, Best Sellers, Full Catalog
- Search by title/author
- Cart with quantity update/remove
- Login/Register (JWT)
- Checkout with UPI/Credit Card/Debit Card
- My Orders section

## Required running services
- `auth-service` on `http://localhost:8081`
- `catalog-service` on `http://localhost:8082`
- `order-service` on `http://localhost:8083`

## Run frontend
```bash
cd "/Users/maramreddysrinivasareddy/Documents/New project/bookstore-microservices/customer-web"
python3 -m http.server 5500
```

Open:
- `http://localhost:5500`

## API base config
If ports change, edit these values in `app.js`:
- `API.auth`
- `API.catalog`
- `API.order`
