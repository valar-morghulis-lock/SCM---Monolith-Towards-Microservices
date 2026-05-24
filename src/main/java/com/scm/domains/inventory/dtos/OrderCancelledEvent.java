package com.scm.domains.inventory.dtos;

import java.util.List;

public record OrderCancelledEvent(
        String orderId,
        List<LineItem> items
) {
    public record LineItem(Integer productId, Integer quantity) {}
}