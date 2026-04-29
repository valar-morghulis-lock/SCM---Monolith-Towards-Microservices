-- 1. Seed Countries
INSERT INTO countries (id, name, iso_code_3) VALUES
(1, 'Egypt', 'EGY'),
(2, 'Saudi Arabia', 'SAU'),
(3, 'United Arab Emirates', 'ARE')
ON CONFLICT (id) DO NOTHING;

-- 2. Seed Cities (linked to Countries)
INSERT INTO cities (id, name, country_id) VALUES
(1, 'New Cairo', 1),
(2, 'Alexandria', 1),
(3, 'Riyadh', 2),
(4, 'Jeddah', 2),
(5, 'Dubai', 3),
(6, 'Abu Dhabi', 3)
ON CONFLICT (id) DO NOTHING;

-- 3. Seed Locations (linked to Cities)
INSERT INTO locations (id, city_id, address_line_1) VALUES
(1, 1, '5th Settlement, New Cairo'),
(2, 2, 'Dekheila Port Area'),
(3, 3, 'King Fahd Rd, Olaya'),
(4, 4, 'Jeddah Islamic Port'),
(5, 5, 'Jebel Ali Free Zone'),
(6, 6, 'Mussafah Industrial')
ON CONFLICT (id) DO NOTHING;

-- 4. Seed Categories
INSERT INTO categories (id, name) VALUES
(1, 'Industrial Tools'),
(2, 'Electronics'),
(3, 'Safety Equipment')
ON CONFLICT (id) DO NOTHING;

-- 5. Seed Products (linked to Categories)
INSERT INTO products (id, sku, name, category_id, description) VALUES
(1, 'DRILL-HD-001', 'Heavy Duty Power Drill', 1, 'Industrial grade 18V cordless drill'),
(2, 'SENS-PRO-02', 'Proximity Sensor', 2, 'Ultrasonic industrial sensor'),
(3, 'HELM-SAFE-X', 'Carbon Fiber Safety Helmet', 3, 'High-impact protection gear')
ON CONFLICT (id) DO NOTHING;

-- 6. Seed Warehouses (linked to Locations)
INSERT INTO warehouses (id, name, location_id) VALUES
(1, 'Cairo Central Hub', 1),
(2, 'Alexandria Port Depot', 2),
(3, 'Dubai Logistics Center', 5)
ON CONFLICT (id) DO NOTHING;

-- 7. Seed Suppliers (linked to Locations)
INSERT INTO suppliers (id, name, location_id) VALUES
(1, 'Global Tools Inc', 3), -- Riyadh location
(2, 'Emirates Tech Supply', 6) -- Abu Dhabi location
ON CONFLICT (id) DO NOTHING;

-- 8. Seed Supplier Products (links Suppliers to Products)
INSERT INTO supplier_products (supplier_id, product_id, supplier_sku, supply_price, lead_time_days) VALUES
(1, 1, 'GT-DRILL-X1', 1250.00, 5),
(2, 2, 'ETS-SENS-99', 450.00, 3)
ON CONFLICT (supplier_id, product_id) DO NOTHING;

-- 9. Seed Initial Stock (links Products to Warehouses)
INSERT INTO stock (product_id, warehouse_id, quantity) VALUES
(1, 1, 50),
(2, 1, 100),
(3, 2, 25),
(1, 3, 75)
ON CONFLICT (product_id, warehouse_id) DO NOTHING;

-- Reset sequences for PostgreSQL (standard practice for Seed files)
SELECT setval(pg_get_serial_sequence('countries', 'id'), coalesce(max(id), 1)) FROM countries;
SELECT setval(pg_get_serial_sequence('cities', 'id'), coalesce(max(id), 1)) FROM cities;
SELECT setval(pg_get_serial_sequence('locations', 'id'), coalesce(max(id), 1)) FROM locations;
SELECT setval(pg_get_serial_sequence('categories', 'id'), coalesce(max(id), 1)) FROM categories;
SELECT setval(pg_get_serial_sequence('products', 'id'), coalesce(max(id), 1)) FROM products;
SELECT setval(pg_get_serial_sequence('warehouses', 'id'), coalesce(max(id), 1)) FROM warehouses;
SELECT setval(pg_get_serial_sequence('suppliers', 'id'), coalesce(max(id), 1)) FROM suppliers;