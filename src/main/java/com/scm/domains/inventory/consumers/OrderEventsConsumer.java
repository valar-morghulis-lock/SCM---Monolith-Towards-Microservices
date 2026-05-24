package com.scm.domains.inventory.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.domains.inventory.dtos.OrderCancelledEvent;
import com.scm.domains.inventory.services.InventoryService;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header; // Crucial for reading header tokens!
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import static com.scm.domains.inventory.generated.tables.ProcessedEvents.PROCESSED_EVENTS;

@Component
public class OrderEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsConsumer.class);

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final DSLContext dsl;

    public OrderEventsConsumer(InventoryService inventoryService, ObjectMapper objectMapper, DSLContext dsl) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.dsl = dsl;
    }

    @KafkaListener(topics = "order-events", groupId = "scm-inventory-saga-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderEvents(
            String message,
            @Header("eventType") String eventType, // Intercepts the record header sent by your relay
            Acknowledgment ack
    ) {
        log.warn("KAFKA CONSUMER: Received incoming event message with type header [{}]", eventType);

        try {
            // Early Gate: Only process the payload if it is an explicit cancellation request
            if (!"ORDER_CANCELLED".equals(eventType)) {
                log.info("KAFKA CONSUMER: Event type [{}] is not handled by inventory compensation. Skipping.", eventType);
                ack.acknowledge(); // Acknowledge to advance past the unneeded event safely
                return;
            }

            OrderCancelledEvent event = objectMapper.readValue(message, OrderCancelledEvent.class);

            // Step 1: Parse the incoming Order ID string back into a structural UUID object
            java.util.UUID orderUuid = java.util.UUID.fromString(event.orderId());

            // Step 2: Generate a deterministic, unique UUID for this specific saga combination
            String uniqueSeedString = "ORDER_CANCELLED_" + orderUuid.toString();
            java.util.UUID uniqueEventKey = java.util.UUID.nameUUIDFromBytes(uniqueSeedString.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Step 3: Insert type-safely into the database
            boolean isNewEvent = dsl.insertInto(PROCESSED_EVENTS)
                    .set(PROCESSED_EVENTS.EVENT_ID, uniqueEventKey)
                    .set(PROCESSED_EVENTS.PROCESSED_AT, java.time.OffsetDateTime.now())
                    .onDuplicateKeyIgnore()
                    .execute() > 0;

            if (!isNewEvent) {
                log.warn("KAFKA CONSUMER: Duplicate OrderCancelled event detected for Order ID: {}. Skipping execution.", event.orderId());
                ack.acknowledge();
                return;
            }

            log.warn("SAGA COMPENSATION: Processing item stock release for Order ID: {} with {} items", event.orderId(), event.items().size());

            for (OrderCancelledEvent.LineItem item : event.items()) {
                inventoryService.releaseStockItem(item.productId(), item.quantity(), event.orderId());
            }

            ack.acknowledge();
            log.warn("KAFKA CONSUMER: Successfully processed and acknowledged event for Order ID: {}", event.orderId());

        } catch (IllegalArgumentException e) {
            log.error("KAFKA CONSUMER ERROR: Invalid UUID format received in event payload. Rejecting message.", e);
            ack.acknowledge(); // Bad payload data format will never fix itself; ack it to unblock the queue
        } catch (Exception e) {
            log.error("KAFKA CONSUMER ERROR: Failed to process order event message. Leaving unacknowledged.", e);
        }
    }
}