-- =========================================================
-- Centrika Order Management System — Schema
-- PostgreSQL 15
-- =========================================================

-- Enums are implemented as CHECK constraints on VARCHAR rather than
-- native PostgreSQL ENUM types. Native enums are cheap to create but
-- expensive to alter (ADD VALUE requires care with transactions in
-- older PG versions, and removing/renaming a value is not supported
-- at all). A CHECK constraint can be dropped and re-added in a single
-- migration with no table rewrite, which matters for a system that
-- will evolve tier/status values over time.

CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    region      VARCHAR(100) NOT NULL,
    tier        VARCHAR(20)  NOT NULL CHECK (tier IN ('standard', 'premium', 'enterprise')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    sku             VARCHAR(64)  NOT NULL UNIQUE,
    category        VARCHAR(100) NOT NULL,
    unit_price      NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    stock_quantity  INTEGER NOT NULL CHECK (stock_quantity >= 0),
    -- optimistic-locking column used by the API layer (Part 2)
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT NOT NULL REFERENCES customers(id),
    status       VARCHAR(20) NOT NULL
                 CHECK (status IN ('pending','processing','shipped','delivered','cancelled')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id          BIGINT NOT NULL REFERENCES products(id),
    quantity            INTEGER NOT NULL CHECK (quantity > 0),
    -- Denormalised on purpose: price at time of purchase must survive
    -- future price changes on the product. This is the one deliberate
    -- denormalisation in the schema (see DESIGN.md).
    unit_price_at_purchase NUMERIC(12,2) NOT NULL CHECK (unit_price_at_purchase >= 0)
);

-- =========================================================
-- Indexes
-- =========================================================

-- orders: almost every query filters/sorts by customer, status, or a
-- created_at range (or a combination). Composite indexes are ordered
-- with the equality-filter column first, range column last, so a
-- single index can serve both an equality lookup and a range scan.
CREATE INDEX idx_orders_customer_created   ON orders (customer_id, created_at DESC);
CREATE INDEX idx_orders_status_created     ON orders (status, created_at DESC);
CREATE INDEX idx_orders_created_at         ON orders (created_at DESC);

-- order_items: joined back to orders/products constantly for revenue
-- aggregation and stock-vs-sales queries.
CREATE INDEX idx_order_items_order_id      ON order_items (order_id);
CREATE INDEX idx_order_items_product_id    ON order_items (product_id);

-- products: stock alerts filter on low stock; category browsing is common.
CREATE INDEX idx_products_stock_quantity   ON products (stock_quantity) WHERE stock_quantity < 20;
CREATE INDEX idx_products_category         ON products (category);

-- customers: tier shows up in every revenue-by-tier rollup.
CREATE INDEX idx_customers_tier            ON customers (tier);
