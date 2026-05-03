package com.scm.domains.inventory.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;
public record InventoryEvent(
        UUID eventId,
        String type,           // e.g., "STOCK_RESERVED", "STOCK_COMMITTED"
        String externalOrderId,
        Integer productId,
        Integer warehouseId,
        Integer quantity,
        OffsetDateTime timestamp
) {}