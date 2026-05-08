-- ============================================================
-- scm_db schema — source of truth for inventory-service
-- jOOQ generates query classes against this schema
--
-- To apply changes:
--   1. Edit this file
--   2. docker-compose down -v && docker-compose up -d
-- ============================================================

ALTER ROLE scm_user SET search_path TO public;
GRANT USAGE ON SCHEMA public TO scm_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO scm_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO scm_user;

-- ── 1. Geography Hierarchy ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS countries (
    id          SERIAL       PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    iso_code_3  CHAR(3)      UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS cities (
    id          SERIAL       PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    country_id  INTEGER      NOT NULL REFERENCES countries(id)
);

CREATE TABLE IF NOT EXISTS locations (
    id             SERIAL       PRIMARY KEY,
    address_line_1 VARCHAR(255) NOT NULL,
    city_id        INTEGER      NOT NULL REFERENCES cities(id)
);

-- ── 2. Core SCM Entities ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS warehouses (
    id          SERIAL       PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    location_id INTEGER      NOT NULL REFERENCES locations(id),
    is_active   BOOLEAN      DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS categories (
    id                 SERIAL       PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    parent_category_id INTEGER      REFERENCES categories(id),
    is_active          BOOLEAN      DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS products (
    id          SERIAL       PRIMARY KEY,
    sku         VARCHAR(50)  UNIQUE NOT NULL,
    name        VARCHAR(200) NOT NULL,
    category_id INTEGER      NOT NULL REFERENCES categories(id),
    description TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS suppliers (
    id          SERIAL       PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    location_id INTEGER      NOT NULL REFERENCES locations(id),
    is_active   BOOLEAN      DEFAULT TRUE
);

-- ── 3. Relationships & Inventory ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS supplier_products (
    supplier_id      INTEGER        NOT NULL REFERENCES suppliers(id),
    product_id       INTEGER        NOT NULL REFERENCES products(id),
    supplier_sku     VARCHAR(100),
    supply_price     DECIMAL(12,2)  NOT NULL,
    lead_time_days   INTEGER        DEFAULT 0,
    PRIMARY KEY (supplier_id, product_id)
);

CREATE TABLE IF NOT EXISTS stock (
    id           SERIAL   PRIMARY KEY,
    product_id   INTEGER  NOT NULL REFERENCES products(id),
    warehouse_id INTEGER  NOT NULL REFERENCES warehouses(id),
    quantity     INTEGER  DEFAULT 0 CHECK (quantity >= 0),
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_product_warehouse UNIQUE (product_id, warehouse_id)
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id             SERIAL       PRIMARY KEY,
    product_id     INTEGER      NOT NULL REFERENCES products(id),
    warehouse_id   INTEGER      NOT NULL REFERENCES warehouses(id),
    change_amount  INTEGER      NOT NULL,
    movement_type  VARCHAR(50),
    remarks        TEXT,
    reference_type VARCHAR(30),
    reservation_id BIGINT,      -- set after stock_reservations is created
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock_reservations (
    id                BIGSERIAL    PRIMARY KEY,
    product_id        INTEGER      NOT NULL REFERENCES products(id),
    warehouse_id      INTEGER      NOT NULL REFERENCES warehouses(id),
    external_order_id VARCHAR(50)  NOT NULL,
    quantity          INTEGER      NOT NULL CHECK (quantity > 0),
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Add FK from stock_movements → stock_reservations after both tables exist
ALTER TABLE stock_movements
    ADD CONSTRAINT fk_movement_reservation
    FOREIGN KEY (reservation_id) REFERENCES stock_reservations(id);

-- ── 4. Outbox & Inbox ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS outbox (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(50)  NOT NULL,
    type           VARCHAR(50)  NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  DEFAULT 'PENDING',
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at   TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── 5. ShedLock ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shedlock (
    name        VARCHAR(64)              NOT NULL,
    lock_until  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by   VARCHAR(255)             NOT NULL,
    PRIMARY KEY (name)
);

-- ── 6. Indexes ─────────────────────────────────────────────────────────────
CREATE UNIQUE INDEX idx_res_external_order_product
    ON stock_reservations (external_order_id, product_id);

CREATE INDEX idx_res_external_order_id
    ON stock_reservations (external_order_id);

CREATE INDEX IF NOT EXISTS idx_stock_prod_wh
    ON stock (product_id, warehouse_id);

CREATE INDEX IF NOT EXISTS idx_res_prod_wh
    ON stock_reservations (product_id, warehouse_id);

CREATE INDEX IF NOT EXISTS idx_res_status_expiry
    ON stock_reservations (status, expires_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_res_updated_at
    ON stock_reservations (updated_at);

CREATE INDEX IF NOT EXISTS idx_reservation_expiry
    ON stock_reservations (expires_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_reservation_atp
    ON stock_reservations (product_id, warehouse_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox (status, created_at)
    WHERE status = 'PENDING';