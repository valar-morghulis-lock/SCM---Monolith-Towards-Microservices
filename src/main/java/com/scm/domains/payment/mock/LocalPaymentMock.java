package com.scm.domains.payment.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

@Component
@Profile("dev") // Seamlessly active in your development workspace
public class LocalPaymentMock {

    private static final Logger log = LoggerFactory.getLogger(LocalPaymentMock.class);
    private static final String OUTBOUND_TOPIC = "payment-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public LocalPaymentMock(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-events", groupId = "payment-mock-group")
    public void handleOrderEvent(String payload, @Header("eventType") String eventType) {
        if (!"ORDER_VALIDATED".equals(eventType)) {
            return;
        }

        log.warn("[MOCK_PAYMENT] Caught ORDER_VALIDATED event. Simulating gateway authorize...");

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String orderId = jsonNode.get("id").asText();
            double totalAmount = jsonNode.get("totalAmount").asDouble();

            TimeUnit.MILLISECONDS.sleep(1200);

            boolean paymentSuccess = totalAmount != 500.00 && !payload.contains("FAIL_PAYMENT");
            String resultingEvent = paymentSuccess ? "PAYMENT_COMPLETED" : "PAYMENT_FAILED";
            String statusValue = paymentSuccess ? "SUCCESS" : "DECLINED";

            String responsePayload = String.format(
                    "{\"orderId\":\"%s\",\"status\":\"%s\",\"processedAt\":\"%s\",\"amount\":%.2f}",
                    orderId, statusValue, OffsetDateTime.now(), totalAmount
            );

            ProducerRecord<String, String> record = new ProducerRecord<>(
                    OUTBOUND_TOPIC,
                    orderId,
                    responsePayload
            );
            record.headers().add(new RecordHeader("eventType", resultingEvent.getBytes()));

            kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
            log.warn("[MOCK_PAYMENT] Settled transaction. Dispatched: [{}] for Order ID: {}", resultingEvent, orderId);

        } catch (Exception e) {
            log.error("[MOCK_PAYMENT] Failed to execute local payment simulation", e);
        }
    }
}