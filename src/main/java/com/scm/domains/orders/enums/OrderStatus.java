package com.scm.domains.orders.enums;


import lombok.Getter;

@Getter
public enum OrderStatus {
    /**
     * Initial state when the order is saved, but stock has not yet been reserved.
     */
    PENDING("Pending"),

    /**
     * Set after the Inventory Service confirms stock reservation via Kafka.
     */
    VALIDATED("Validated"),

    /**
     * Set if the Inventory Service reports insufficient stock.
     */
    CANCELLED("Cancelled"),

    /**
     * Order has been picked and handed to the carrier.
     */
    SHIPPED("Shipped"),

    /**
     * Final successful state of the order.
     */
    COMPLETED("Completed");

    private final String displayValue;

    OrderStatus(String displayValue) {
        this.displayValue = displayValue;
    }
}