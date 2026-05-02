package com.scm.domains.inventory.dtos;

import java.time.OffsetDateTime;

public record ReservationResponse(
        Long reservationId,
        String status,
        OffsetDateTime expiresAt
) {}