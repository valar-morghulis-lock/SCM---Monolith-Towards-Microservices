package com.scm.domains.inventory.services;

import com.scm.domains.inventory.generated.tables.records.OutboxRecord;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.scm.domains.inventory.generated.Tables.OUTBOX;

@Service
public class InventoryOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxRelay.class);

    private final DSLContext dsl;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InventoryOutboxRelay(DSLContext dsl, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 2000)
    @SchedulerLock(name = "scm_outbox_relay", lockAtLeastFor = "1s", lockAtMostFor = "1m")
    public void processOutbox() {
        // No class-level @Transactional – we manage transactions per event
        while (true) {
            OutboxRecord event = tryClaimOneEvent();
            if (event == null) break;

            boolean delivered = false;
            try {
                // Synchronous send with timeout
                kafkaTemplate.send("inventory-events", event.getAggregateId(), event.getPayload().data()).get(5, TimeUnit.SECONDS);  // wait for broker ack
                delivered = true;
            } catch (Exception ex) {
                log.warn("Failed to send event {}: {}", event.getId(), ex.getMessage());
            }

            updateEventStatus(event.getId(), delivered ? "PROCESSED" : "FAILED");
        }
    }

    private OutboxRecord tryClaimOneEvent() {
        // Use a short transaction: select one pending row with lock, then commit immediately?
        // Better: use a separate transaction for the claim. Since we are using jOOQ without Spring's
        // transactional template, we can use dsl.transactionResult().
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            OutboxRecord record = tx.selectFrom(OUTBOX).where(OUTBOX.STATUS.eq("PENDING")).orderBy(OUTBOX.CREATED_AT.asc()).limit(1).forUpdate().skipLocked().fetchOne();
            if (record != null) {
                // Optionally mark as "IN_PROGRESS" to avoid double processing if we crash
                tx.update(OUTBOX).set(OUTBOX.STATUS, "IN_PROGRESS").where(OUTBOX.ID.eq(record.getId())).execute();
            }
            return record;
        });
    }

    private void updateEventStatus(UUID eventId, String status) {
        dsl.update(OUTBOX).set(OUTBOX.STATUS, status).set(OUTBOX.PROCESSED_AT, OffsetDateTime.now()).where(OUTBOX.ID.eq(eventId)).execute();
    }
}
