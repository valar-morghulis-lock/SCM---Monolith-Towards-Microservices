package com.scm.domains.orders.consumers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.domains.orders.entities.Order;
import com.scm.domains.orders.enums.OrderStatus;
import com.scm.domains.orders.repositories.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class OrderKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderKafkaConsumer.class);

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public OrderKafkaConsumer(OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "inventory-events",
            groupId = "order-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional("orderTransactionManager")
    public void onInventoryEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {

        try {
            var envelope = objectMapper.readTree(message);
            String eventType = envelope.get("eventType") != null
                    ? envelope.get("eventType").asText()
                    : envelope.get("type") != null
                    ? envelope.get("type").asText()
                    : "UNKNOWN";

            log.info("Received inventory event [{}]", eventType);

            switch (eventType) {
                case "INVENTORY_RESERVED" -> updateOrderStatus(envelope, OrderStatus.VALIDATED);
                case "INVENTORY_FAILED"   -> updateOrderStatus(envelope, OrderStatus.CANCELLED);
                default -> log.debug("Ignoring unhandled inventory event: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", e.getMessage(), e);
        }
    }

    private void updateOrderStatus(com.fasterxml.jackson.databind.JsonNode envelope,
                                   OrderStatus newStatus) {
        try {
            // Navigate to the 'payload' node first
            JsonNode payloadNode = envelope.get("payload");

            String orderId = (payloadNode != null && payloadNode.get("orderId") != null)
                    ? payloadNode.get("orderId").asText()
                    : null;

            if (orderId == null) {
                log.warn("Inventory event missing orderId in payload — cannot update order");
                return;
            }

            Order order = orderRepository.findById(UUID.fromString(orderId))
                    .orElse(null);

            if (order == null) {
                log.warn("Order {} not found in database — check if UUID is correct", orderId);
                return;
            }

            order.setStatus(newStatus);
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepository.save(order);

            log.info("Order {} status successfully updated to {}", orderId, newStatus);

        } catch (Exception e) {
            log.error("Failed to update order status: {}", e.getMessage(), e);
        }
    }
}