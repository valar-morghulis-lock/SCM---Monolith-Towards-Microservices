package com.scm.exceptions.domains.inventory;

import lombok.Getter;

/**
 * Thrown when an inventory operation (like reservation or deduction)
 * exceeds the Available to Promise (ATP) quantity.
 */

public class InsufficientStockException extends RuntimeException {

    private final Integer productId;
    private final Integer requestedQuantity;
    private final Integer availableQuantity;

    public InsufficientStockException(String message) {
        super(message);
        this.productId = null;
        this.requestedQuantity = null;
        this.availableQuantity = null;
    }

    public InsufficientStockException(String message, Integer productId, Integer requested, Integer available) {
        super(message);
        this.productId = productId;
        this.requestedQuantity = requested;
        this.availableQuantity = available;
    }

    public Integer getProductId() { return productId; }
    public Integer getRequestedQuantity() { return requestedQuantity; }
    public Integer getAvailableQuantity() { return availableQuantity; }
}