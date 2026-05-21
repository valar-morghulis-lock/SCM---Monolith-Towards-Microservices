package com.scm.domains.orders.relay;

import com.scm.domains.orders.entities.OrderOutbox;
import com.scm.domains.orders.repositories.OrderOutboxRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OrderOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxRelay.class);
    private static final String TOPIC = "order-events";

    private final OutboxInternalService outboxInternalService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Manual constructor for dependency injection
    public OrderOutboxRelay(OutboxInternalService outboxInternalService,
                            KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxInternalService = outboxInternalService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${scm.relay.fixed-delay:2000}")  // 30s during development
    @SchedulerLock(name = "order_outbox_relay", lockAtLeastFor = "5s", lockAtMostFor = "1m")
    public void processOutbox() {
        // Step 1: Transactionally mark records as IN_PROGRESS
        List<OrderOutbox> workQueue = outboxInternalService.fetchAndLockPending();

        for (OrderOutbox event : workQueue) {
            boolean delivered = false;
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        TOPIC,
                        event.getAggregateId(),
                        event.getPayload()
                );

                record.headers().add(new RecordHeader("eventType", event.getType().getBytes()));

                // Blocking network call happens OUTSIDE a database transaction
                kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
                delivered = true;
                log.info("Published [{}] for Order {}", event.getType(), event.getAggregateId());
            }  catch (Exception ex) {
            log.error("Kafka link failure: Stopping batch processing.");
            outboxInternalService.updateFinalStatus(event.getId(), false);
            return; // Don't even try the other 9 messages in the workQueue
        }

            // Step 2: Update status in a separate transaction
            outboxInternalService.updateFinalStatus(event.getId(), delivered);
        }
    }

    @Service
    public static class OutboxInternalService {
        private final OrderOutboxRepository repository;

        // Manual constructor
        public OutboxInternalService(OrderOutboxRepository repository) {
            this.repository = repository;
        }

        @Transactional(value = "orderTransactionManager", propagation = Propagation.REQUIRES_NEW)
        public List<OrderOutbox> fetchAndLockPending() {
            List<OrderOutbox> pending = repository.findTop10ByStatusOrderByCreatedAtAsc("PENDING");
            pending.forEach(e -> e.setStatus("IN_PROGRESS"));
            return repository.saveAll(pending);
        }

        @Transactional(value = "orderTransactionManager", propagation = Propagation.REQUIRES_NEW)
        public void updateFinalStatus(UUID id, boolean success) {
            repository.findById(id).ifPresent(event -> {
                if (success) {
                    event.setStatus("PROCESSED");
                    event.setProcessedAt(OffsetDateTime.now());
                } else {
                    // Keep it PENDING so the scheduler picks it up again
                    event.setStatus("PENDING");
                }
                repository.save(event);
            });
        }
    }
}