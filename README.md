# Shoppy Backend (Spring Boot)

REST API for **Shoppy**, a full-stack e-commerce marketplace. Sellers list products with photos, buyers pay through Stripe Checkout, and everyone sees stock changes live over WebSockets.

- **Frontend:** Next.js 14, [shopping-frontend](https://github.com/mahalingam-dev-8/shopping-frontend)
- **Live demo:** https://shopping-frontend-ebon.vercel.app

## Demo

▶ [Watch the full flow (≈90 s)](https://www.loom.com/share/9613a8520bd04a83b266ff9ff50295b4): a seller lists a product, a buyer sees it appear live over WebSockets, pays with Stripe, and the webhook creates the order shown in the buyer's orders and the seller's sales.

## Stack

Java 21 · Spring Boot 3.3 · Spring Security · Spring Data JPA · PostgreSQL · Flyway · JWT (jjwt) · Stripe · S3-compatible storage · WebSocket (STOMP) · Docker · springdoc OpenAPI

## API

Swagger UI is served at `/api`.

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/` | public | Health check |
| POST | `/users` | public | Sign up as USER (customer) or ADMIN (seller) |
| POST | `/auth/login` | public | Log in. Sets the JWT in an HttpOnly `Authentication` cookie |
| GET | `/users/me` | user | Current user |
| POST | `/products` | admin | Create a product |
| POST | `/products/{id}/image` | owner/admin | Upload a JPEG, PNG or WebP product photo (max 2 MB) |
| GET | `/products?status=available` | user | List products (`available` = unsold only) |
| GET | `/products/{id}` | user | Product detail |
| POST | `/checkout/session` | user | Create a Stripe Checkout session |
| POST | `/checkout/webhook` | Stripe | Stripe webhook. Signature-verified and idempotent |
| GET | `/orders` | user | My orders |
| GET | `/orders/all` | admin | Orders for the admin's own products, with the buyer |

Errors use the shape `{ "statusCode": 400, "message": "..." | ["..."], "error": "Bad Request" }`.

**Real-time updates:** connect to `/ws` over STOMP with an `Authentication` CONNECT header holding the JWT. Then subscribe to `/topic/products`, which receives `{"event":"updated"}` whenever a product is created, gets an image, or sells.

## Design notes

- **Auth.** A stateless JWT is carried in an HttpOnly cookie. A custom filter (`JwtCookieAuthFilter`) authenticates each request and loads the role from the database, so role changes apply immediately. Admin-only routes use `@PreAuthorize`.
- **Roles.** USER is a customer and ADMIN is a seller, and users choose between them at signup. That is safe because ADMIN has no access to other users' data: sellers manage only their own products and see orders only for those products.
- **Payments.**
  - The Stripe webhook is verified against the raw request body (`Webhook.constructEvent`).
  - An order is created only for a `paid` session.
  - Fulfilment is idempotent. It checks for an existing order, and a UNIQUE `orders.product_id` constraint catches concurrent duplicate deliveries.
- **Uploads.**
  - The image type is detected from the file's leading bytes, not the client's Content-Type.
  - Only the product's owner (or an admin) can upload.
  - The image URL is stored on the product, so listing products is a single query.
  - Storage goes through the S3 API, so AWS S3 or any S3-compatible store (Supabase Storage, Cloudflare R2) works by configuration alone.
- **Money.** Prices are stored as `NUMERIC(10,2)` and converted to Stripe's smallest currency unit (paise) with explicit rounding. The charge currency is INR (`STRIPE_CURRENCY`), the same currency the storefront displays.
- **Schema.** Flyway owns the schema (`src/main/resources/db/migration`), and Hibernate never alters tables.

## Run locally

Requires Java 21, Maven 3.9+ and PostgreSQL.

```bash
createdb shoppy
cp .env.example .env        # then fill in the values
mvn spring-boot:run
```

On startup, Flyway creates the tables. The API listens on `http://localhost:3001` (or `PORT`). Logs are also written to `logs/shoppy.log`.

To test Stripe webhooks locally:

```bash
stripe listen --forward-to localhost:3001/checkout/webhook
# copy the printed whsec_... into STRIPE_WEBHOOK_SECRET
```

Or run it in Docker:

```bash
docker build -t shoppy-backend .
docker run --env-file .env -p 8080:8080 shoppy-backend
```

## Deployment

The backend ships as a Docker image (multi-stage `Dockerfile`) and runs on any container platform. Everything is configured through the environment variables in `.env.example`.

- **Port:** the app listens on `PORT`, as set by the platform.
- **Small instances:** the container is tuned for 512 MB (`JAVA_OPTS` in the Dockerfile).
- **Database:** any PostgreSQL. Flyway migrates it on startup.
- **Storage:** uses the S3 API, so it works with AWS S3 or any S3-compatible store by changing `S3_ENDPOINT` and `S3_PUBLIC_URL_BASE` only.
- **AWS Elastic Beanstalk:** `buildspec.yml` (CodeBuild) and `Procfile` package and run the jar.
