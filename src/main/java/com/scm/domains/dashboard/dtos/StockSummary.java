package com.scm.domains.dashboard.dtos;

import java.util.UUID;

public record StockSummary(
        UUID productId,
        String productName,
        String sku,
        Integer totalQuantity,
        Integer warehouseCount
) {}