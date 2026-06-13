package com.scm.domains.dashboard.dtos;

public record OrderMetrics(
        Integer totalOrdersCount,
        java.math.BigDecimal totalRevenue,
        Integer cancelledOrdersCount
) {}