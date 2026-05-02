package com.scm.domains.inventory.services;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static com.scm.domains.generated.Tables.STOCK_RESERVATIONS;

@Service
public class InventoryCleanupService {

    private static final Logger log = LoggerFactory.getLogger(InventoryCleanupService.class);
    private final DSLContext dsl;

    public InventoryCleanupService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Safety Net: Releases stock for reservations that were never confirmed.
     * Runs daily at 6:00 AM, aligning with the circuit migration schedule.
     * Configured now to run every minute for testing purposes
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void releaseExpiredReservations() {
        log.warn("Starting scheduled inventory cleanup: Releasing expired reservations.");

        int affectedRows = dsl.update(STOCK_RESERVATIONS)
                .set(STOCK_RESERVATIONS.STATUS, "EXPIRED")
                .set(STOCK_RESERVATIONS.UPDATED_AT, OffsetDateTime.now())
                .where(STOCK_RESERVATIONS.STATUS.eq("PENDING"))
                .and(STOCK_RESERVATIONS.EXPIRES_AT.lt(OffsetDateTime.now()))
                .execute();

        if (affectedRows > 0) {
            log.warn("Inventory Cleanup Complete: {} reservations moved to EXPIRED status.", affectedRows);
        } else {
            log.warn("Inventory Cleanup: No expired reservations found.");
        }
    }
}