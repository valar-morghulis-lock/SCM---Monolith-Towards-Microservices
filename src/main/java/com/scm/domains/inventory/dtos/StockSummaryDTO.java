package com.scm.domains.inventory.dtos;

import java.math.BigDecimal;

public record StockSummaryDTO(
        String sku,
        String name,
        String category,
        BigDecimal totalQuantity
) {}