-- ============================================================
-- ord_db schema — source of truth for order-service
-- Hibernate validates against this on every startup (ddl-auto=validate)
--
-- To apply changes:
--   1. Edit this file
--   2. docker-compose down -v && docker-compose up -d
-- ============================================================

ALTER ROLE ord_user SET search_path TO public;

GRANT USAGE ON SCHEMA public TO ord_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ord_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ord_user;

-- ── ShedLock (distributed scheduler lock) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS public.shedlock (
    name        VARCHAR(64)              NOT NULL,
    lock_until  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by   VARCHAR(255)             NOT NULL,
    PRIMARY KEY (name)
);

-- ── Orders ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.orders (
    id           UUID                     PRIMARY KEY,
    order_number VARCHAR(50)              UNIQUE NOT NULL,
    customer_id  UUID                     NOT NULL,
    status       VARCHAR(20)              NOT NULL, -- PENDING, VALIDATED, SHIPPED, CANCELLED
    total_amount DECIMAL(19,4)            NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── Order Items ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.order_items (
    id         UUID          PRIMARY KEY,
    order_id   UUID          NOT NULL,
    product_id UUID          NOT NULL, -- Logical FK to Inventory Service
    sku        VARCHAR(100)  NOT NULL,
    quantity   INTEGER       NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(19,4) NOT NULL,
    CONSTRAINT fk_order FOREIGN KEY (order_id)
        REFERENCES public.orders(id) ON DELETE CASCADE
);

-- ── Order Outbox (transactional outbox pattern) ────────────────────────────
CREATE TABLE IF NOT EXISTS public.order_outbox (
    id             UUID                     PRIMARY KEY,
    aggregate_type VARCHAR(50)              NOT NULL, -- e.g. 'ORDER'
    aggregate_id   VARCHAR(50)              NOT NULL, -- the Order ID
    type           VARCHAR(50)              NOT NULL, -- e.g. 'ORDER_CREATED'
    payload        JSONB                    NOT NULL, -- full event payload
    status VARCHAR(20) NOT NULL, -- PENDING, VALIDATED, SHIPPED, CANCELLED, COMPLETED
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at   TIMESTAMP WITH TIME ZONE
);

-- Relay service picks up PENDING events ordered by creation time
CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON public.order_outbox (status, created_at)
    WHERE status = 'PENDING';