package com.scm.domains.inventory.services;

import com.scm.domains.inventory.dtos.ReservationRequest;
import com.scm.domains.inventory.dtos.ReservationResponse;
import com.scm.exceptions.domains.inventory.InsufficientStockException;
import com.scm.exceptions.domains.inventory.InventoryException;
import com.scm.exceptions.domains.inventory.ResourceNotFoundException;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static com.scm.domains.generated.Tables.*;

@Service
public class StockReservationService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final DSLContext dsl;

    public StockReservationService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public ReservationResponse reserveStock(ReservationRequest request) {
        // 1. Lock the physical stock row
        var physicalStock = dsl.select(STOCK.QUANTITY)
                .from(STOCK)
                .where(STOCK.PRODUCT_ID.eq(request.productId()))
                .and(STOCK.WAREHOUSE_ID.eq(request.warehouseId()))
                .forUpdate()
                .fetchOptional(STOCK.QUANTITY)
                .orElse(0);

        // 2. Calculate current active reservations
        var activeReservations = dsl.select(DSL.sum(STOCK_RESERVATIONS.QUANTITY))
                .from(STOCK_RESERVATIONS)
                .where(STOCK_RESERVATIONS.PRODUCT_ID.eq(request.productId()))
                .and(STOCK_RESERVATIONS.WAREHOUSE_ID.eq(request.warehouseId()))
                .and(STOCK_RESERVATIONS.STATUS.eq("PENDING"))
                .and(STOCK_RESERVATIONS.EXPIRES_AT.gt(OffsetDateTime.now()))
                .fetchOptionalInto(Integer.class)
                .orElse(0);

        int atp = physicalStock - activeReservations;

        // 3. Validation with detailed Exception context
        if (atp < request.quantity()) {
            log.info("Stock reservation failed: Product {} in Warehouse {}. Requested: {}, Available: {}",
                    request.productId(), request.warehouseId(), request.quantity(), atp);

            throw new InsufficientStockException(
                    "Insufficient inventory for the requested quantity.",
                    request.productId(),
                    request.quantity(),
                    atp
            );
        }

        // 4. Create the reservation
        OffsetDateTime expiry = OffsetDateTime.now().plusMinutes(request.ttlMinutes());

        var reservationId = dsl.insertInto(STOCK_RESERVATIONS)
                .set(STOCK_RESERVATIONS.PRODUCT_ID, request.productId())
                .set(STOCK_RESERVATIONS.WAREHOUSE_ID, request.warehouseId())
                .set(STOCK_RESERVATIONS.EXTERNAL_ORDER_ID, request.externalOrderId())
                .set(STOCK_RESERVATIONS.QUANTITY, request.quantity())
                .set(STOCK_RESERVATIONS.STATUS, "PENDING")
                .set(STOCK_RESERVATIONS.EXPIRES_AT, expiry)
                .returning(STOCK_RESERVATIONS.ID)
                .fetchOne(STOCK_RESERVATIONS.ID);

        log.info("Successfully reserved {} units for Order {} (Reservation ID: {})",
                request.quantity(), request.externalOrderId(), reservationId);

        return new ReservationResponse(reservationId, "PENDING", expiry);
    }


    @Transactional
    public void confirmReservation(String externalOrderId) {
        // 1st. Find and validate the reservation (Time-Aware)
        var reservation = dsl.selectFrom(STOCK_RESERVATIONS)
                .where(STOCK_RESERVATIONS.EXTERNAL_ORDER_ID.eq(externalOrderId))
                .and(STOCK_RESERVATIONS.STATUS.eq("PENDING"))
                .and(STOCK_RESERVATIONS.EXPIRES_AT.gt(OffsetDateTime.now()))
                .fetchOptional()
                .orElseThrow(() -> new ResourceNotFoundException("Valid Stock Reservation", externalOrderId));

        // 2nd. GATEKEEPER: Lock the physical STOCK row before deduction
        var currentQuantity = dsl.select(STOCK.QUANTITY)
                .from(STOCK)
                .where(STOCK.PRODUCT_ID.eq(reservation.getProductId()))
                .and(STOCK.WAREHOUSE_ID.eq(reservation.getWarehouseId()))
                .forUpdate() // <--- Prevents concurrent "Lost Updates"
                .fetchOptional(STOCK.QUANTITY)
                .orElseThrow(() -> new InventoryException("STOCK_RECORD_MISSING",
                        "Physical stock record disappeared for Product: " + reservation.getProductId(), 500));

        // 3rd. Final Safety Check: Ensure the deduction won't result in negative stock
        if (currentQuantity < reservation.getQuantity()) {
            log.error("CRITICAL: Physical stock ({}) is less than reserved quantity ({}) for Order {}. " +
                            "This indicates a manual stock adjustment or integrity failure.",
                    currentQuantity, reservation.getQuantity(), externalOrderId);
            throw new InventoryException("INCONSISTENT_STOCK", "Physical stock is insufficient to fulfill reservation.", 500);
        }

        // 4th. Physically deduct the stock
        dsl.update(STOCK)
                .set(STOCK.QUANTITY, STOCK.QUANTITY.minus(reservation.getQuantity()))
                .where(STOCK.PRODUCT_ID.eq(reservation.getProductId()))
                .and(STOCK.WAREHOUSE_ID.eq(reservation.getWarehouseId()))
                .execute();

        // 5th. Mark reservation as COMMITTED
        dsl.update(STOCK_RESERVATIONS)
                .set(STOCK_RESERVATIONS.STATUS, "COMMITTED")
                .set(STOCK_RESERVATIONS.UPDATED_AT, OffsetDateTime.now())
                .where(STOCK_RESERVATIONS.ID.eq(reservation.getId()))
                .execute();

        log.info("Inventory Committed: Order {} confirmed. Physical deduction complete.", externalOrderId);
    }

    @Transactional
    public void cancelReservation(String externalOrderId) {
        int affected = dsl.update(STOCK_RESERVATIONS)
                .set(STOCK_RESERVATIONS.STATUS, "CANCELLED")
                .set(STOCK_RESERVATIONS.UPDATED_AT, OffsetDateTime.now())
                .where(STOCK_RESERVATIONS.EXTERNAL_ORDER_ID.eq(externalOrderId))
                .and(STOCK_RESERVATIONS.STATUS.eq("PENDING"))
                .execute();

        if (affected > 0) {
            log.info("Manual Release: Order {} was cancelled. Stock is now available in the pool.", externalOrderId);
        } else {
            log.info("Cancel Request: No active pending reservation found for Order {}.", externalOrderId);
        }
    }
}