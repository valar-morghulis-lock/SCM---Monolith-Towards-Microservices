package com.scm.domains.orders.relay;

import com.scm.domains.orders.entities.OrderOutbox;
import com.scm.domains.orders.repositories.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OrderOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxRelay.class);
    private static final String TOPIC = "order-events";

    private final OrderOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderOutboxRelay(OrderOutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @SchedulerLock(name = "order_outbox_relay", lockAtLeastFor = "1s", lockAtMostFor = "1m")
    @Transactional("orderTransactionManager")
    public void processOutbox() {
        List<OrderOutbox> pending = outboxRepository.findTop10ByStatusOrderByCreatedAtAsc("PENDING");

        for (OrderOutbox event : pending) {
            event.setStatus("IN_PROGRESS");
            outboxRepository.save(event);

            boolean delivered = false;
            try {
                // Wrap the payload and add the 'type' as a header
                org.apache.kafka.clients.producer.ProducerRecord<String, String> record =
                        new org.apache.kafka.clients.producer.ProducerRecord<>(
                                TOPIC,
                                event.getAggregateId(),
                                event.getPayload()
                        );

                // Essential fix: Send the event type in the header
                record.headers().add(new org.apache.kafka.common.header.internals.RecordHeader(
                        "eventType", event.getType().getBytes()));

                kafkaTemplate.send(record).get(5, java.util.concurrent.TimeUnit.SECONDS);
                delivered = true;
                log.info("Published [{}] for Order {}", event.getType(), event.getAggregateId());
            } catch (Exception ex) {
                log.warn("Failed to publish [{}] for Order {}: {}",
                        event.getType(), event.getAggregateId(), ex.getMessage());
            }

            event.setStatus(delivered ? "PROCESSED" : "FAILED");
            event.setProcessedAt(OffsetDateTime.now());
            outboxRepository.save(event);
        }
    }
}