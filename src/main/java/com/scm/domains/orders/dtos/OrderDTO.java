package com.scm.domains.orders.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for Orders.
 * Uses Jakarta Validation to enforce business rules before persistence.
 */
public record OrderDTO(
        UUID id,

        @NotBlank(message = "Order number is required")
        @Size(min = 3, max = 50, message = "Order number must be between 3 and 50 characters")
        String orderNumber,

        @NotNull(message = "Customer ID is required")
        UUID customerId,

        // Status is often managed internally by the Service,
        // but validated here for incoming update requests.
        String status,

        @PositiveOrZero(message = "Total amount cannot be negative")
        @DecimalMax(value = "999999999.9999", message = "Total amount exceeds maximum precision")
        BigDecimal totalAmount,

        @NotEmpty(message = "Order must contain at least one item")
        @Valid // CRITICAL: Ensures the validation of each OrderItemDTO in the list
        List<OrderItemDTO> items
) {}