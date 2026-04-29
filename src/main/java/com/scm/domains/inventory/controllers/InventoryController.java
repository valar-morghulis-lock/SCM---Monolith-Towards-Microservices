package com.scm.domains.inventory.controllers;

import com.scm.domains.inventory.services.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    @Autowired
    private InventoryService inventoryService;

    /**
     * Replaces the old test-stock.
     * Returns a global view of all products and their total stock across the network.
     */
    @GetMapping("/summary")
    public ResponseEntity<List<?>> getGlobalSummary() {
        log.warn("REST Request: Fetching global inventory summary");
        return ResponseEntity.ok(inventoryService.getGlobalStockSummary());
    }

    /**
     * Returns a granular view: Which warehouse holds which product,
     * including City and Country details.
     */
    @GetMapping("/warehouse-report")
    public ResponseEntity<List<?>> getWarehouseReport() {
        return ResponseEntity.ok(inventoryService.getStockByWarehouse());
    }

    /**
     * Useful for Master-Detail views in the UI.
     */
    @GetMapping("/products/category/{categoryId}")
    public ResponseEntity<List<?>> getProductsByCategory(@PathVariable Integer categoryId) {
        return ResponseEntity.ok(inventoryService.getProductsByCategory(categoryId));
    }

    /**
     * SCM Procurement endpoint: Find who supplies a product and at what cost.
     */
    @GetMapping("/suppliers/{productId}")
    public ResponseEntity<List<?>> getSuppliers(@PathVariable Integer productId) {
        return ResponseEntity.ok(inventoryService.getSuppliersForProduct(productId));
    }

    @PostMapping("/movement")
    public ResponseEntity<String> recordMovement(
            @RequestParam Integer productId,
            @RequestParam Integer warehouseId,
            @RequestParam Integer change,
            @RequestParam String type, // e.g., INBOUND, OUTBOUND, ADJUSTMENT
            @RequestParam(required = false) String remarks) {

        try {
            inventoryService.processMovement(productId, warehouseId, change, type, remarks);
            return ResponseEntity.ok("Movement recorded successfully.");
        } catch (IllegalArgumentException e) {
            // Specifically, catching business logic failures (like negative stock)
            log.warn("Inventory validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Internal transaction failure", e);
            return ResponseEntity.internalServerError().body("An unexpected error occurred.");
        }
    }

    @GetMapping("/history/{productId}")
    public ResponseEntity<List<?>> getHistory(@PathVariable Integer productId) {
        return ResponseEntity.ok(inventoryService.getProductMovementHistory(productId));
    }
}