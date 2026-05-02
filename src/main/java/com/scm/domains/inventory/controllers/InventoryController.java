package com.scm.domains.inventory.controllers;

import com.scm.domains.inventory.dtos.StockSummaryDTO;
import com.scm.domains.inventory.services.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    @Autowired
    private InventoryService inventoryService;

    /**
     * Replaces the old test-stock.
     * Returns a global view of all products and their total stock across the network.
     */
    @GetMapping("/summary")
    public ResponseEntity<List<StockSummaryDTO>> getGlobalSummary() {
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

    /**
     * If productId is invalid, InventoryService throws ResourceNotFoundException,
     * GlobalExceptionHandler catches it and returns 404.
     */
    @PostMapping("/movement")
    public ResponseEntity<Map<String, String>> recordMovement(
            @RequestParam Integer productId,
            @RequestParam Integer warehouseId,
            @RequestParam Integer change,
            @RequestParam String type,
            @RequestParam(required = false) String remarks) {

        log.warn("REST Request: Recording {} movement for Product {}", type, productId);

        inventoryService.processMovement(productId, warehouseId, change, type, remarks);

        return ResponseEntity.ok(Map.of("message", "Movement recorded successfully."));
    }
    /**
     * Retrieve the movement history for a certain product, INBOUND/OUTBOUND, Quantity changed.
     */
    @GetMapping("/history/{productId}")
    public ResponseEntity<List<?>> getHistory(@PathVariable Integer productId) {
        return ResponseEntity.ok(inventoryService.getProductMovementHistory(productId));
    }
}