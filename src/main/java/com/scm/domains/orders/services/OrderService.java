package com.scm.domains.orders.services;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.domains.orders.dtos.OrderDTO;
import com.scm.domains.orders.entities.Order;
import com.scm.domains.orders.entities.OrderItem;
import com.scm.domains.orders.entities.OrderOutbox;
import com.scm.domains.orders.enums.OrderStatus;
import com.scm.domains.orders.mappers.OrderMapper;
import com.scm.domains.orders.repositories.OrderOutboxRepository;
import com.scm.domains.orders.repositories.OrderRepository;
import com.scm.exceptions.BusinessException;
import com.scm.exceptions.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    private static final Logger _LOGGER = LoggerFactory.getLogger(OrderService.class);

    public OrderService(OrderRepository orderRepository,
                        OrderOutboxRepository outboxRepository,
                        OrderMapper orderMapper,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;

        _LOGGER.warn("OrderService initialized with all dependencies.");
    }


    @Transactional("orderTransactionManager")
    public OrderDTO createOrder(OrderDTO orderDto) {
        _LOGGER.warn("Processing new order creation: {}", orderDto.orderNumber());

        Order order = orderMapper.toEntity(orderDto);
        order.setStatus(OrderStatus.PENDING);

        if (orderDto.items() != null && !orderDto.items().isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;

            for (var itemDto : orderDto.items()) {
                var item = new OrderItem();
                item.setProductId(itemDto.productId());
                item.setSku(itemDto.sku());
                item.setQuantity(itemDto.quantity());
                item.setUnitPrice(itemDto.unitPrice());

                // Calculate line total and add to order total
                BigDecimal lineTotal = itemDto.unitPrice().multiply(new BigDecimal(itemDto.quantity()));
                total = total.add(lineTotal);

                order.addItem(item);
            }
            order.setTotalAmount(total);
        } else {
            _LOGGER.warn("Order {} created with no items.", orderDto.orderNumber());
            order.setTotalAmount(BigDecimal.ZERO);
        }

        Order savedOrder = orderRepository.save(order);
        saveToOutbox(savedOrder, "ORDER_CREATED");

        return orderMapper.toDto(savedOrder);
    }


    @Transactional(value = "orderTransactionManager", readOnly = true)
    public OrderDTO getOrder(UUID id) {
        return orderRepository.findByIdWithItems(id)
                .map(orderMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order with ID " + id + " was not found.",
                        "ORDER_NOT_FOUND"
                ));
    }

    @Transactional("orderTransactionManager")
    public void cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new IllegalStateException("Cannot cancel a shipped order");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(OffsetDateTime.now());

        orderRepository.save(order);

        // Create Outbox entry for ORDER_CANCELLED
        saveToOutbox(order, "ORDER_CANCELLED");
        _LOGGER.warn("Order {} marked as CANCELLED", orderId);
    }
    /**
     * Generic outbox saver to capture any Order-related event.
     */
    private void saveToOutbox(Order order, String eventType) {
        try {
            OrderOutbox outbox = new OrderOutbox();
            outbox.setAggregateType("ORDER");
            outbox.setAggregateId(order.getId().toString());
            outbox.setType(eventType);

            // Serialize current state via DTO to avoid lazy-loading issues in the relay
            String payload = objectMapper.writeValueAsString(orderMapper.toDto(order));
            outbox.setPayload(payload);

            outbox.setStatus("PENDING");
            outbox.setCreatedAt(OffsetDateTime.now());

            outboxRepository.save(outbox);

            _LOGGER.warn("Outbox event [{}] persisted for Order ID: {}", eventType, order.getId());
        } catch (Exception e) {
            // Critical: We throw a RuntimeException to trigger @Transactional rollback.
            // If the outbox fails, the Order must not be saved.
            _LOGGER.error("Outbox serialization failure for Order {}: {}", order.getId(), e.getMessage());
            throw new RuntimeException("Transaction rolled back: Failed to capture outbox event", e);
        }
    }
}