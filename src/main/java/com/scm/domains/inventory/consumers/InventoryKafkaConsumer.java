package com.scm.domains.inventory.consumers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.domains.inventory.dtos.ReservationRequest;
import com.scm.domains.inventory.services.InventoryOutboxWriter;
import com.scm.domains.inventory.services.StockReservationService;
import com.scm.exceptions.domains.inventory.InsufficientStockException;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.scm.domains.inventory.generated.Tables.PRODUCTS;

@Component
public class InventoryKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryKafkaConsumer.class);
    private static final int DEFAULT_WAREHOUSE_ID = 1;
    private static final int RESERVATION_TTL_MINUTES = 30;

    private final StockReservationService reservationService;
    private final InventoryOutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final DSLContext dsl;                          // ← injected for SKU lookup

    public InventoryKafkaConsumer(StockReservationService reservationService,
                                  InventoryOutboxWriter outboxWriter,
                                  ObjectMapper objectMapper,
                                  DSLContext dsl) {
        this.reservationService = reservationService;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.dsl = dsl;
    }

    @KafkaListener(
            topics = "order-events",
            groupId = "inventory-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "eventType", defaultValue = "UNKNOWN") String eventType) { //  Direct header injection

        try {
            // 2. Clear, structured logging
            log.info("Processing event [{}] for Order ID: {}", eventType, key);

            // 3. Simple routing based on header metadata
            switch (eventType) {
                case "ORDER_CREATED" -> {
                    JsonNode payload = objectMapper.readTree(message);
                    handleOrderCreated(key, payload);
                }
                case "ORDER_CANCELLED" -> {
                    // Future-proofing: add handleOrderCancelled(key, payload) here
                    log.info("Order {} cancellation received - skipping inventory reservation", key);
                }
                default -> log.warn("Received unsupported event type [{}] for Order {}", eventType, key);
            }

        } catch (Exception e) {
            log.error("Critical failure processing Order {}: Error: {}", key, e.getMessage(), e);


            if (e.getCause() instanceof org.jooq.exception.IntegrityConstraintViolationException) {
                log.warn("Non-retryable integrity violation for Order {} — skipping retry", key);
                return; // Commit the offset, move on
            }

            throw new RuntimeException("Retryable processing failure", e);
        }
    }

    private void handleOrderCreated(String orderId, JsonNode envelope) throws Exception {
        JsonNode payload = envelope.path("payload").isTextual()
                ? objectMapper.readTree(envelope.path("payload").asText())
                : envelope;

        JsonNode items = payload.path("items");

        if (items.isMissingNode() || items.isEmpty()) {
            log.warn("ORDER_CREATED event for {} has no items — skipping", orderId);
            publishResult(orderId, false);
            return;
        }

        // 1. Extract all unique SKUs from the items list
        Set<String> skus = new java.util.HashSet<>();
        for (JsonNode item : items) {
            String sku = item.path("sku").asText(null);
            if (sku != null && !sku.isBlank()) {
                skus.add(sku);
            }
        }

        // 2. Batch resolve SKUs to Product IDs in a single jOOQ query
        Map<String, Integer> skuToIdMap = dsl.select(PRODUCTS.SKU, PRODUCTS.ID)
                .from(PRODUCTS)
                .where(PRODUCTS.SKU.in(skus))
                .fetchMap(PRODUCTS.SKU, PRODUCTS.ID);

        boolean allReserved = true;

        // 3. Process reservations using the pre-fetched map
        for (JsonNode item : items) {
            String sku      = item.path("sku").asText(null);
            int quantity    = item.path("quantity").asInt(0);

            if (sku == null || sku.isBlank()) {
                log.warn("Item in order {} is missing SKU — cannot resolve product", orderId);
                allReserved = false;
                continue;
            }

            Integer productId = skuToIdMap.get(sku);

            if (productId == null) {
                log.warn("No product found for SKU [{}] in order {} — skipping item", sku, orderId);
                allReserved = false;
                continue;
            }

            try {
                reservationService.reserveStock(new ReservationRequest(
                        productId,
                        DEFAULT_WAREHOUSE_ID,
                        orderId,
                        quantity,
                        RESERVATION_TTL_MINUTES
                ));
                log.info("Reserved {} units of SKU [{}] (productId={}) for order {}",
                        quantity, sku, productId, orderId);

            } catch (InsufficientStockException ex) {
                log.warn("Insufficient stock for SKU [{}] in order {}: {}", sku, orderId, ex.getMessage());
                allReserved = false;

            } catch (org.jooq.exception.IntegrityConstraintViolationException ex) {
                // Idempotency guard — reservation already exists for this order+product
                // This happens on Kafka retry after a partial success
                log.warn("Reservation already exists for SKU [{}] in order {} — treating as already reserved", sku, orderId);
                // Don't set allReserved = false — the reservation is there, just duplicate attempt
            }
        }

        publishResult(orderId, allReserved);
    }

    private void publishResult(String orderId, boolean allReserved) throws Exception {
        String eventType = allReserved ? "INVENTORY_RESERVED" : "INVENTORY_FAILED";

        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("orderId", orderId);
        resultMap.put("status",  allReserved ? "RESERVED" : "FAILED");

        outboxWriter.write(orderId, eventType, objectMapper.writeValueAsString(resultMap));
        log.info("Inventory result [{}] written to outbox for Order {}", eventType, orderId);
    }
}