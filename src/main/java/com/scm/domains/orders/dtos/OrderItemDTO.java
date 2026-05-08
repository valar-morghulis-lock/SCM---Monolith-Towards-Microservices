package com.scm.domains.orders.dtos;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDTO(
        @NotNull(message = "Product ID is required")
        UUID productId,
        @NotBlank(message = "SKU is required")
        String sku,
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        @NotNull(message = "Unit price is required")
        @Positive(message = "Unit price must be greater than zero")
        BigDecimal unitPrice
) {}