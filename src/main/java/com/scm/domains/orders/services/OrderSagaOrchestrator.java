package com.scm.domains.orders.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.domains.orders.entities.OrderOutbox;
import com.scm.domains.orders.repositories.OrderOutboxRepository;
import com.scm.domains.orders.repositories.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderSagaOrchestrator(OrderRepository orderRepository,
                                 OrderOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Success Path: Payment cleared. Advance order to PAID so logistics can pack it.
     */
    @Transactional("orderTransactionManager")
    public void approveOrder(String orderId) {
        log.info("SAGA: Approving order processing for ID: {}", orderId);

        // 1. Update order status to PAID and validate row mutation
        int updatedRows = orderRepository.updateStatus(UUID.fromString(orderId), "PAID");
        if (updatedRows == 0) {
            throw new EntityNotFoundException("SAGA ABORT: Order not found for ID: " + orderId);
        }

        // 2. Write to outbox so Inventory/Logistics knows to start packaging
        try {
            String payload = objectMapper.writeValueAsString(new OrderPaidPayload(orderId));

            OrderOutbox outboxEvent = new OrderOutbox();
            outboxEvent.setAggregateId(orderId);
            outboxEvent.setType("ORDER_PAID");
            outboxEvent.setPayload(payload);
            outboxEvent.setStatus("PENDING");
            outboxEvent.setCreatedAt(OffsetDateTime.now());

            outboxRepository.save(outboxEvent);
            log.info("SAGA Outbox: Staged ORDER_PAID event for Order {}", orderId);
        } catch (Exception e) {
            log.error("Failed to serialize SAGA success payload for order {}", orderId, e);
            throw new RuntimeException(e); // Triggers rollback
        }
    }

    /**
     * Compensation Path: Payment failed. Roll back order state and trigger inventory release.
     */
    @Transactional("orderTransactionManager")
    public void compensateOrderCancellation(String orderId) {
        log.warn("SAGA COMPENSATION: Reversing order reservations for ID: {}", orderId);

        // 1. Updating order status to CANCELLED and validating row mutation
        int updatedRows = orderRepository.updateStatus(UUID.fromString(orderId), "CANCELLED");
        if (updatedRows == 0) {
            throw new EntityNotFoundException("SAGA COMPENSATION ABORT: Order not found for ID: " + orderId);
        }

        // 2. Write to outbox to alert InventoryService to release the reserved items
        try {
            String payload = objectMapper.writeValueAsString(new OrderCancelledPayload(orderId));

            OrderOutbox outboxEvent = new OrderOutbox();
            outboxEvent.setAggregateId(orderId);
            outboxEvent.setType("ORDER_CANCELLED");
            outboxEvent.setPayload(payload);
            outboxEvent.setStatus("PENDING");
            outboxEvent.setCreatedAt(OffsetDateTime.now());

            outboxRepository.save(outboxEvent);
            log.warn("SAGA Outbox: Staged ORDER_CANCELLED event for Order {}", orderId);
        } catch (Exception e) {
            log.error("Failed to serialize SAGA compensation payload for order {}", orderId, e);
            throw new RuntimeException(e); // Triggers rollback
        }
    }

    private record OrderPaidPayload(String orderId) {}
    private record OrderCancelledPayload(String orderId) {}
}