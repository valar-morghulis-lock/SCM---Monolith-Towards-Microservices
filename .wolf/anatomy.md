# anatomy.md

> Auto-maintained by OpenWolf. Last scanned: 2026-05-12T06:02:15.753Z
> Files: 70 tracked | Anatomy hits: 0 | Misses: 0

## ./

- `.gitattributes` — Git attributes (~11 tok)
- `.gitignore` — Git ignore rules (~282 tok)
- `CLAUDE.md` — OpenWolf (~57 tok)
- `docker-compose.yml` — Docker Compose services (~1262 tok)
- `domains.iml` (~86 tok)
- `HELP.md` — Getting Started (~399 tok)
- `mvnw` — or more contributor license agreements.  See the NOTICE file (~3144 tok)
- `mvnw.cmd` — Declares Directory (~2262 tok)
- `pom.xml` — Maven project configuration (~2111 tok)
- `README.md` — Project documentation (~89 tok)

## .claude/

- `settings.json` (~441 tok)

## .claude/rules/

- `openwolf.md` (~313 tok)

## .mvn/wrapper/

- `maven-wrapper.properties` (~46 tok)

## docker/ord_db/

- `init.sql` — ord_db schema — source of truth for order-service (~867 tok)

## docker/prometheus/

- `prometheus.yml` (~72 tok)

## docker/scm_db/

- `V1__Schema.sql` — scm_db schema — source of truth for inventory-service (~1927 tok)
- `V2__Data.sql` — 1. Seed Countries (~872 tok)

## src/main/java/com/scm/config/

- `InventoryDbConfig.java` — Configuration: InventoryDbConfig (~507 tok)
- `JacksonConfig.java` — Configuration: JacksonConfig (~242 tok)
- `KafkaConsumerConfig.java` — Configuration: KafkaConsumerConfig (~846 tok)
- `KafkaProducerConfig.java` — Configuration: KafkaProducerConfig (~490 tok)
- `OrderDbConfig.java` — Configuration: OrderDbConfig (~1113 tok)
- `QuerydslConfig.java` — Configuration: QuerydslConfig (~122 tok)
- `ShedLockConfig.java` — Configuration: ShedLockConfig (~304 tok)

## src/main/java/com/scm/domains/

- `DomainsApplication.java` — DomainsApplication: main (~248 tok)

## src/main/java/com/scm/domains/inventory/consumers/

- `InventoryKafkaConsumer.java` — Component: InventoryKafkaConsumer (~2093 tok)

## src/main/java/com/scm/domains/inventory/controllers/

- `InventoryController.java` — Replaces the old test-stock. (~900 tok)
- `InventoryReservationController.java` — RestController: InventoryReservationController (4 endpoints) (~557 tok)

## src/main/java/com/scm/domains/inventory/dtos/

- `InventoryEvent.java` — Class: InventoryEvent (~111 tok)
- `ReservationRequest.java` — Class: ReservationRequest (~120 tok)
- `ReservationResponse.java` — Class: ReservationResponse (~59 tok)
- `StockSummaryDTO.java` — Class: StockSummaryDTO (~62 tok)

## src/main/java/com/scm/domains/inventory/mappers/

- `InventoryMapper.java` — Helper to bridge jOOQ Map Objects to String (~378 tok)

## src/main/java/com/scm/domains/inventory/relay/

- `InventoryOutboxRelay.java` — Wraps the raw payload and metadata into a single JSON envelope (~1312 tok)

## src/main/java/com/scm/domains/inventory/services/

- `InventoryCleanupService.java` — Safety Net: Releases stock for reservations that were never confirmed. (~575 tok)
- `InventoryOutboxWriter.java` — Service: InventoryOutboxWriter (~413 tok)
- `InventoryService.java` — Aggregates total stock per product across all warehouses, (~1805 tok)
- `StockReservationService.java` — Service: StockReservationService (~1977 tok)

## src/main/java/com/scm/domains/orders/consumers/

- `OrderKafkaConsumer.java` — Component: OrderKafkaConsumer (~1072 tok)

## src/main/java/com/scm/domains/orders/controllers/

- `OrderController.java` — RestController: OrderController (3 endpoints) (~399 tok)

## src/main/java/com/scm/domains/orders/dtos/

- `OrderDTO.java` — Data Transfer Object for Orders. (~336 tok)
- `OrderItemDTO.java` — Class: OrderItemDTO (~177 tok)

## src/main/java/com/scm/domains/orders/entities/

- `Order.java` — Entity: Order (~1080 tok)
- `OrderItem.java` — Entity: OrderItem (~710 tok)
- `OrderOutbox.java` — Map the String to PostgreSQL JSONB type natively. (~957 tok)

## src/main/java/com/scm/domains/orders/enums/

- `OrderStatus.java` — Initial state when the order is saved, but stock has not yet been reserved. (~230 tok)

## src/main/java/com/scm/domains/orders/mappers/

- `OrderMapper.java` — Class: OrderMapper (~250 tok)

## src/main/java/com/scm/domains/orders/relay/

- `OrderOutboxRelay.java` — Service: OrderOutboxRelay (~1136 tok)

## src/main/java/com/scm/domains/orders/repositories/

- `OrderOutboxRepository.java` — Finds events that haven't been processed yet. (~224 tok)
- `OrderQueryRepository.java` — Class: OrderQueryRepository (~84 tok)
- `OrderQueryRepositoryImpl.java` — Repository: OrderQueryRepositoryImpl (~1041 tok)
- `OrderRepository.java` — Repository: OrderRepository (~210 tok)

## src/main/java/com/scm/domains/orders/services/

- `OrderService.java` — Generic outbox saver to capture any Order-related event. (~1572 tok)

## src/main/java/com/scm/exceptions/

- `BusinessException.java` — BusinessException: getErrorCode, getStatus (~140 tok)
- `EntityNotFoundException.java` — Class: EntityNotFoundException (~63 tok)
- `ErrorResponse.java` — Class: ErrorResponse (~65 tok)
- `GlobalExceptionHandler.java` — Controller: GlobalExceptionHandler (~1266 tok)
- `ScmErrorCode.java` — ScmErrorCode: getCode (~86 tok)

## src/main/java/com/scm/exceptions/domains/inventory/

- `DuplicateResourceException.java` — Class: DuplicateResourceException (~81 tok)
- `InsufficientStockException.java` — Thrown when an inventory operation (like reservation or deduction) (~313 tok)
- `InvalidOperationException.java` — Use for stock issues or invalid order states (~97 tok)
- `InventoryException.java` — Class: InventoryException (~83 tok)
- `ResourceNotFoundException.java` — Class: ResourceNotFoundException (~108 tok)

## src/main/resources/

- `application-dev.properties` — application-dev.properties (cleaned up) (~439 tok)
- `application-prod.properties` (~24 tok)
- `application-test.properties` — application-test.properties (~423 tok)
- `application.properties` (~335 tok)
- `schema.sql` — Database schema (~52 tok)

## src/test/java/com/scm/config/

- `TestDbConfig.java` — TestDbConfig: orderDataSource, inventoryDataSource (~275 tok)

## src/test/java/com/scm/domains/

- `DomainsApplicationTests.java` — Class: DomainsApplicationTests (~158 tok)
