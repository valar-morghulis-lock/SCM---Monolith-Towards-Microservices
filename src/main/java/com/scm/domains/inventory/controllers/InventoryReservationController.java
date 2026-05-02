package com.scm.domains.inventory.controllers;

import com.scm.domains.inventory.dtos.ReservationRequest;
import com.scm.domains.inventory.dtos.ReservationResponse;
import com.scm.domains.inventory.services.StockReservationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/reservations")
public class InventoryReservationController {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationController.class);
    private final StockReservationService reservationService;

    public InventoryReservationController(StockReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        log.warn("REST Request: Reservation for Order {} and Product {}",
                request.externalOrderId(), request.productId());

        ReservationResponse response = reservationService.reserveStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{externalOrderId}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable String externalOrderId) {
        log.warn("REST Request: Confirming Inventory for Order {}", externalOrderId);
        reservationService.confirmReservation(externalOrderId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{externalOrderId}")
    public ResponseEntity<Void> cancel(@PathVariable String externalOrderId) {
        log.warn("REST Request: Cancelling Inventory for Order {}", externalOrderId);
        reservationService.cancelReservation(externalOrderId);
        return ResponseEntity.noContent().build();
    }
}