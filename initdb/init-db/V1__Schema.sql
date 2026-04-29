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