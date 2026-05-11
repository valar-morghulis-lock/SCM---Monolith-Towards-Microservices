# SCM — Modular Monolith Towards Microservices

A production-grade Supply Chain Management core built to demonstrate the deliberate evolution from a centralized monolith to an event-driven distributed system. The project uses **Java 21** and **Spring Boot 3.4** to handle complex warehouse transactions with correctness guarantees that survive process crashes, network failures, and concurrent writes.

---

## Why this project exists

Most portfolio projects demonstrate CRUD over a single database. This one demonstrates the harder problems: what happens when a business transaction spans two databases, a message broker, and three independent services — and any one of them can fail mid-flight? The answer is the architecture described below.

---

## Architecture overview

```
┌─────────────────────────────────────────────────────────┐
│                    REST API (port 8080)                  │
│               Spring Cloud Gateway (planned)             │
└────────────────────────┬────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
   Order Domain    Inventory Domain   (future)
   PostgreSQL       PostgreSQL        Payment
   (ord_db:5433)   (scm_db:5434)     Service
        │                │
        ▼                ▼
   order_outbox       outbox
   (Outbox table)  (Outbox table)
        │                │
        ▼                ▼
   ┌─────────────────────────┐
   │     Apache Kafka        │
   │  order-events           │
   │  inventory-events       │
   └─────────────────────────┘
```

Each domain owns its database exclusively. No cross-domain joins. No shared schema. Services communicate only through Kafka events.

---

## Tech stack

| Layer | Technology | Why |
|---|---|---|
| Runtime | Java 21 + Virtual Threads | Handles high-concurrency warehouse transactions without thread exhaustion |
| Framework | Spring Boot 3.4 | Production-grade dependency injection, transaction management, scheduling |
| Order persistence | Spring Data JPA + Hibernate | Relational integrity for orders, line items, approval workflows |
| Inventory persistence | jOOQ | Type-safe SQL with pessimistic locking for concurrent stock updates |
| Messaging | Apache Kafka | Reliable, ordered, replayable event delivery between domains |
| Reliability pattern | Transactional Outbox | Guarantees events are published even if the process crashes after a DB write |
| Distributed locking | ShedLock | Prevents duplicate relay execution when multiple app instances run |
| Schema management | PostgreSQL init scripts | Versioned, reproducible schema via Docker volume mounts |
| Observability | Spring Actuator + structured logging | Health endpoints, distributed trace IDs on every log line |

---

## The Order lifecycle — a distributed Saga

This is the core of the project. A single "place order" request touches two databases and two Kafka topics, with automatic compensation if any step fails.

```
1. POST /api/v1/orders
   └─ OrderService
      ├─ INSERT into public.orders          (status: PENDING)
      ├─ INSERT into public.order_items
      └─ INSERT into public.order_outbox    (type: ORDER_CREATED)
         └─ all three in ONE transaction — atomic

2. OrderOutboxRelay (scheduled)
   └─ reads order_outbox WHERE status = PENDING
   └─ publishes to Kafka: order-events
   └─ marks outbox row PROCESSED

3. InventoryKafkaConsumer
   └─ consumes ORDER_CREATED from order-events
   └─ resolves SKUs → product IDs via jOOQ batch lookup
   └─ StockReservationService.reserveStock()
      ├─ SELECT ... FOR UPDATE (pessimistic lock on stock row)
      ├─ calculates Available-to-Promise = on_hand - active_reservations
      ├─ INSERT into stock_reservations     (status: PENDING, TTL: 30min)
      └─ writes INVENTORY_RESERVED to inventory outbox

4. InventoryOutboxRelay (scheduled)
   └─ reads outbox WHERE status = PENDING
   └─ publishes to Kafka: inventory-events
   └─ marks outbox row PROCESSED

5. OrderKafkaConsumer
   └─ consumes INVENTORY_RESERVED from inventory-events
   └─ UPDATE orders SET status = VALIDATED
```

**Failure handling:**
- If the app crashes between steps 1 and 2, the outbox row stays PENDING and gets picked up on the next relay cycle — the order is never lost.
- If inventory reservation fails (insufficient stock), `INVENTORY_FAILED` is published instead, and the order is set to `CANCELLED` — the Saga compensates automatically.
- Duplicate Kafka delivery (at-least-once) is handled idempotently via the unique constraint on `stock_reservations.external_order_id`.

---

## Key patterns implemented

### Transactional Outbox

The central reliability guarantee. When `OrderService` saves an order, it writes an `ORDER_CREATED` event to the `order_outbox` table **in the same database transaction**. If Kafka is down, the order still saves correctly. The relay will publish the event once Kafka recovers. Without this pattern, a crash between "save order" and "publish to Kafka" would silently drop the event.

```java
// One atomic transaction — both succeed or both roll back
orderRepository.save(order);
outboxRepository.save(outboxEvent);
```

### Pessimistic locking for inventory

Stock updates use `SELECT ... FOR UPDATE` to prevent two concurrent requests from both seeing "50 units available" and both succeeding, resulting in -50 units on hand. The lock is held only for the duration of the update, minimising contention.

```java
dsl.selectFrom(STOCK)
   .where(STOCK.PRODUCT_ID.eq(productId))
   .forUpdate()     // ← held until transaction commits
   .fetchOne();
```

### Available-to-Promise (ATP) calculation

Physical stock minus active (non-expired) reservations gives the true available quantity. This prevents overselling even when multiple orders are in-flight simultaneously.

```
ATP = quantity_on_hand - SUM(active reservations)
```

### Choreography Saga with compensating transactions

No central orchestrator. Each service reacts to Kafka events and either moves the Saga forward or publishes a compensating event to undo previous steps. This keeps services fully decoupled — Inventory doesn't know Order exists, and vice versa.

---

## Planned evolution: from polling to CDC

The current relay polls the outbox every 30 seconds (configurable via `scm.relay.fixed-delay`). The production upgrade path is **Debezium Change Data Capture**, which reads PostgreSQL's Write-Ahead Log directly. This eliminates polling latency entirely — events reach Kafka within milliseconds of the DB commit, with zero extra DB load.

```
Current:   DB write → wait up to 30s → relay polls → Kafka publish
Debezium:  DB write → WAL entry → Debezium reads → Kafka publish (~ms)
```

Debezium is intentionally deferred to keep the current implementation readable and debuggable. The outbox table structure and Kafka topics are already compatible with a Debezium migration.

---

## Database schema

### ord_db (Order domain)

| Table | Purpose |
|---|---|
| `orders` | Order header — status, customer, total |
| `order_items` | Line items with SKU, quantity, unit price |
| `order_outbox` | Transactional outbox for order events |
| `shedlock` | Distributed lock state for relay scheduler |

### scm_db (Inventory domain)

| Table | Purpose |
|---|---|
| `products` | Product catalog with SKU |
| `stock` | Physical stock per product per warehouse |
| `stock_reservations` | Active reservations with TTL and ATP tracking |
| `stock_movements` | Immutable audit trail of all stock changes |
| `warehouses` / `locations` / `countries` | Geography hierarchy |
| `suppliers` / `supplier_products` | Supplier catalog and pricing |
| `outbox` | Transactional outbox for inventory events |
| `processed_events` | Inbox deduplication table |
| `shedlock` | Distributed lock state for relay scheduler |

---

## Running locally

**Prerequisites:** Java 21, Maven 3.9+, Docker Desktop

```bash
# Clone
git clone https://github.com/valar-morghulis-lock/SCM---Monolith-Towards-Microservices.git
cd SCM---Monolith-Towards-Microservices

# Start infrastructure (PostgreSQL x2, Kafka, pgAdmin, Prometheus, Grafana)
docker-compose up -d

# Verify containers are healthy
docker-compose ps

# Run the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Service endpoints:**

| Service | URL |
|---|---|
| REST API | http://localhost:8080 |
| pgAdmin | http://localhost:5050 (admin@scm.com / admin) |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |

**Place a test order:**

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderNumber": "ORD-2026-001",
    "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "totalAmount": 0,
    "items": [
      { "productId": "7b2e89d1-3b5a-4f5c-8d1e-2b3c4d5e6f7a", "sku": "DRILL-HD-001", "quantity": 2, "unitPrice": 150.50 },
      { "productId": "8c3f90e2-4c6b-4e6d-9e2f-3c4d5e6f7a8b", "sku": "SENS-PRO-02",  "quantity": 1, "unitPrice": 45.00  }
    ]
  }'
```

Within 30 seconds (relay interval) the order status will transition from `PENDING` → `VALIDATED` as the inventory reservation completes through Kafka.

---

## Roadmap

- [x] Order domain — create, submit, lifecycle management
- [x] Inventory domain — ATP calculation, pessimistic locking, reservations
- [x] Transactional Outbox pattern — order and inventory domains
- [x] Kafka event flow — ORDER_CREATED → INVENTORY_RESERVED → VALIDATED
- [x] Choreography Saga with compensation (INVENTORY_FAILED → CANCELLED)
- [ ] Payment Service — consume ORDER_VALIDATED, process funds
- [ ] Compensating transactions — release reservations on payment failure
- [ ] Logistics — physical fulfilment, shipping manifest generation
- [ ] Debezium CDC — replace polling relay with WAL-based event capture
- [ ] API Gateway — Spring Cloud Gateway with JWT auth
- [ ] Kubernetes deployment — Helm charts, HPA, health probes
