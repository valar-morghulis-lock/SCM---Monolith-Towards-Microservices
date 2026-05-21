package com.scm.domains.orders.consumers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.domains.orders.services.OrderSagaOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class OrderPaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentConsumer.class);
    private final ObjectMapper objectMapper;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    public OrderPaymentConsumer(ObjectMapper objectMapper, OrderSagaOrchestrator orderSagaOrchestrator) {
        this.objectMapper = objectMapper;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
    }


    @KafkaListener(topics = "payment-events", groupId = "order-payment-consumer-group")
    public void consumePaymentResult(String payload, @Header("eventType") String eventType) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String orderId = jsonNode.get("orderId").asText();

            if ("PAYMENT_COMPLETED".equals(eventType)) {
                log.info("Payment confirmed for Order {}. Advancing state machine...", orderId);
                orderSagaOrchestrator.approveOrder(orderId);

            } else if ("PAYMENT_FAILED".equals(eventType)) {
                log.warn("Payment DECLINED for Order {}. Initializing compensating transaction...", orderId);
                orderSagaOrchestrator.compensateOrderCancellation(orderId);
            }

        } catch (Exception e) {
            log.error("Failed to safely route incoming payment milestone event", e);
        }
    }
}