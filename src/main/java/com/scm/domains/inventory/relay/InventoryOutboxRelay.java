package com.scm.domains.inventory.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scm.domains.inventory.generated.tables.records.OutboxRecord;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.scm.domains.inventory.generated.Tables.OUTBOX;

@Service
public class InventoryOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxRelay.class);
    private static final String TOPIC = "inventory-events";

    private final DSLContext dsl;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InventoryOutboxRelay(DSLContext dsl,
                                KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${scm.relay.fixed-delay:2000}")  // 30s during development
    @SchedulerLock(name = "scm_outbox_relay", lockAtLeastFor = "5s", lockAtMostFor = "1m")
    public void processOutbox() {
        int processedCount = 0;
        int failCount = 0;

        while (processedCount < 100) { // Safety cap per execution
            OutboxRecord event = tryClaimOneEvent();
            if (event == null) break;

            try {
                String envelope = createEventEnvelope(event);
                kafkaTemplate.send(TOPIC, event.getAggregateId(), envelope)
                        .get(5, TimeUnit.SECONDS);

                updateEventStatus(event.getId(), "PROCESSED");
                processedCount++;
                failCount = 0; // Reset fail count on success
            } catch (Exception ex) {
                log.error("KAFKA DOWN: Aborting batch. Event {} will stay PENDING.", event.getId());
                updateEventStatus(event.getId(), "PENDING");
                return; // Kill the current scheduled execution immediately
            }
        }
    }

    private OutboxRecord tryClaimOneEvent() {
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);

            // SKIP LOCKED prevents worker collision
            OutboxRecord record = tx.selectFrom(OUTBOX)
                    .where(OUTBOX.STATUS.eq("PENDING"))
                    .orderBy(OUTBOX.CREATED_AT.asc())
                    .limit(1)
                    .forUpdate()
                    .skipLocked()
                    .fetchOne();

            if (record != null) {
                tx.update(OUTBOX)
                        .set(OUTBOX.STATUS, "IN_PROGRESS")
                        .where(OUTBOX.ID.eq(record.getId()))
                        .execute();
            }
            return record;
        });
    }

    /**
     * Wraps the raw payload and metadata into a single JSON envelope
     * so the consumer can identify the event type.
     */
    private String createEventEnvelope(OutboxRecord event) throws Exception {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventType", event.getType());
        envelope.put("aggregateId", event.getAggregateId());
        envelope.put("timestamp", OffsetDateTime.now().toString());

        // Payload is already a JSONB string, parse it into the node
        envelope.set("payload", objectMapper.readTree(event.getPayload().data()));

        return objectMapper.writeValueAsString(envelope);
    }

    private void updateEventStatus(UUID eventId, String status) {
        dsl.update(OUTBOX)
                .set(OUTBOX.STATUS, status)
                .set(OUTBOX.PROCESSED_AT, OffsetDateTime.now())
                .where(OUTBOX.ID.eq(eventId))
                .execute();
    }
}