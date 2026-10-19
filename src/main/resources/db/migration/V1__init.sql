CREATE TABLE users (
    id       BIGSERIAL PRIMARY KEY,
    email    VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(16)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255)   NOT NULL,
    description TEXT           NOT NULL,
    price       NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    user_id     BIGINT         NOT NULL REFERENCES users (id),
    sold        BOOLEAN        NOT NULL DEFAULT FALSE,
    image_url   VARCHAR(512)
);

CREATE INDEX idx_products_sold ON products (sold);

-- product_id is UNIQUE: a product can be ordered once, which is also what makes
-- duplicate Stripe webhook deliveries safe.
CREATE TABLE orders (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    product_id BIGINT      NOT NULL UNIQUE REFERENCES products (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
