package com.scm.domains.inventory.services;

import com.scm.domains.orders.services.OrderService;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.scm.domains.inventory.generated.Tables.OUTBOX;

@Service
public class InventoryOutboxWriter {
    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxWriter.class);

    private final DSLContext dsl;

    public InventoryOutboxWriter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public void write(String aggregateId, String eventType, String payload) {
        dsl.insertInto(OUTBOX)
                .set(OUTBOX.ID, UUID.randomUUID())
                .set(OUTBOX.AGGREGATE_TYPE, "INVENTORY")
                .set(OUTBOX.AGGREGATE_ID, aggregateId)
                .set(OUTBOX.TYPE, eventType) // Ensure this maps to eventType or event_type in consumer
                .set(OUTBOX.PAYLOAD, JSONB.valueOf(payload))
                .set(OUTBOX.STATUS, "PENDING")
                // .set(OUTBOX.CREATED_AT, LocalDateTime.now()) // Recommended for polling logic
                .execute();

        log.debug("Outbox record persisted for aggregateId: {} with type: {}", aggregateId, eventType);
    }
}