package com.scm.domains.inventory.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
        @NotNull Integer productId,
        @NotNull Integer warehouseId,
        @NotBlank String externalOrderId,
        @Min(1) Integer quantity,
        @Min(1) Integer ttlMinutes
) {}

