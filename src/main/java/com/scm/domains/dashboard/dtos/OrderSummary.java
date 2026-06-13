package com.scm.domains.dashboard.dtos;

import java.util.UUID;

public class OrderSummary {
    private final String orderId;
    private final String customerName;
    private final String status;
    private final Double totalAmount;
    private final String createdAt;

    public OrderSummary(String orderId, String customerName, String status, Double totalAmount, String createdAt) {
        this.orderId = orderId;
        this.customerName = customerName;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    // These must exist and return non-null values
    public String getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public String getStatus() { return status; }
    public Double getTotalAmount() { return totalAmount; }
    public String getCreatedAt() { return createdAt; }
}