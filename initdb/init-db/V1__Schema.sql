-- 1. Geography Hierarchy
CREATE TABLE countries (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    iso_code_3 CHAR(3) UNIQUE NOT NULL
);

CREATE TABLE cities (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    country_id INTEGER NOT NULL REFERENCES countries(id)
);

CREATE TABLE locations (
    id SERIAL PRIMARY KEY,
    address_line_1 VARCHAR(255) NOT NULL,
    city_id INTEGER NOT NULL REFERENCES cities(id)
);

-- 2. Core SCM Entities
CREATE TABLE warehouses (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    location_id INTEGER NOT NULL REFERENCES locations(id),
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_category_id INTEGER REFERENCES categories(id),
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    category_id INTEGER NOT NULL REFERENCES categories(id),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE suppliers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    location_id INTEGER NOT NULL REFERENCES locations(id),
    is_active BOOLEAN DEFAULT TRUE
);

-- 3. Relationships & Inventory
CREATE TABLE supplier_products (
    supplier_id INTEGER NOT NULL REFERENCES suppliers(id),
    product_id INTEGER NOT NULL REFERENCES products(id),
    supplier_sku VARCHAR(100),
    supply_price DECIMAL(12, 2) NOT NULL,
    lead_time_days INTEGER DEFAULT 0,
    PRIMARY KEY (supplier_id, product_id)
);

CREATE TABLE stock (
    id SERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES products(id),
    warehouse_id INTEGER NOT NULL REFERENCES warehouses(id),
    quantity INTEGER DEFAULT 0 CHECK (quantity >= 0), -- Prevent negative stock
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_product_warehouse UNIQUE (product_id, warehouse_id)
);

CREATE TABLE stock_movements (
    id SERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES products(id),
    warehouse_id INTEGER NOT NULL REFERENCES warehouses(id),
    change_amount INTEGER NOT NULL,
    movement_type VARCHAR(50), -- e.g., 'INBOUND', 'OUTBOUND', 'ADJUSTMENT'
    remarks TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE stock_reservations (
    id BIGSERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES products(id),
    warehouse_id INTEGER NOT NULL REFERENCES warehouses(id),
    external_order_id VARCHAR(50) NOT NULL, -- UUID or ID from the OMS
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, COMMITTED, EXPIRED, CANCELLED
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL, -- TTL for the reservation
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexing for fast TTL cleanup and ATP (Available to Promise) calculations
CREATE INDEX idx_reservation_expiry ON stock_reservations(expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_reservation_atp ON stock_reservations(product_id, warehouse_id) WHERE status = 'PENDING';




-- Adding a link to trace the origin of a stock deduction
ALTER TABLE stock_movements
ADD COLUMN reservation_id BIGINT REFERENCES stock_reservations(id);



-- Optional: Track why a movement happened (e.g., 'ORDER_FULFILLMENT')
ALTER TABLE stock_movements
ADD COLUMN reference_type VARCHAR(30);


-- Optimize searches by Order ID (used in confirm and cancel methods)
CREATE UNIQUE INDEX idx_res_external_order_id
ON STOCK_RESERVATIONS (EXTERNAL_ORDER_ID);

-- Optimize ATP calculations and stock locking
CREATE INDEX idx_stock_prod_wh
ON STOCK (PRODUCT_ID, WAREHOUSE_ID);

-- Optimize foreign key lookups for reservations
CREATE INDEX idx_res_prod_wh
ON STOCK_RESERVATIONS (PRODUCT_ID, WAREHOUSE_ID);


-- Optimize the 6:00 AM safety net and ATP filtering @Scheduled Task for releasing the stock
--This will be called later via Events if transitioned to microservices @Kafka
CREATE INDEX idx_res_status_expiry
ON STOCK_RESERVATIONS (STATUS, EXPIRES_AT)
WHERE STATUS = 'PENDING';


-- Optimize time-series analysis for Tableau reporting
CREATE INDEX idx_res_updated_at
ON STOCK_RESERVATIONS (UPDATED_AT);