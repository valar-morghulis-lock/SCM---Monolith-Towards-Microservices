package com.scm.domains.orders.controllers;

import com.scm.domains.dashboard.dtos.OrderMetrics;
import com.scm.domains.dashboard.dtos.OrderSummary;
import com.scm.domains.orders.repositories.OrderDashboardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class OrderGraphQLController {

    private static final Logger log = LoggerFactory.getLogger(OrderGraphQLController.class);
    private final OrderDashboardRepository orderDashboardRepository;

    public OrderGraphQLController(OrderDashboardRepository orderDashboardRepository) {
        this.orderDashboardRepository = orderDashboardRepository;
    }

    @QueryMapping // Maps directly to 'recentOrdersMetrics(daysAgo: Int!)'
    public OrderMetrics recentOrdersMetrics(@Argument int daysAgo) {
        log.warn("GRAPHQL QUERY: Calculating order analytic matrix metrics via JPA.");
        OffsetDateTime targetTime = OffsetDateTime.now().minusDays(daysAgo);
        return orderDashboardRepository.getMetricsSince(targetTime);
    }

    @QueryMapping
    public List<OrderSummary> recentOrdersList(@Argument int limit) {
        log.warn("GRAPHQL QUERY: Fetching flat order summaries.");
        OffsetDateTime lookbackWindow = OffsetDateTime.now().minusDays(30);

        var orders = orderDashboardRepository.findRecentOrdersWithItems(lookbackWindow);

        // 1. If the repository returns null, return an empty list
        if (orders == null) {
            log.warn("Repository returned null, returning empty list.");
            return Collections.emptyList();
        }

        // 2. Filter out null entries AND map the items
        return orders.stream()
                .filter(Objects::nonNull)
                .limit(limit)
                .map(o -> {
                    // Log what is being processed
                    log.info("Mapping entity: {}", o.getId());
                    return new OrderSummary(
                            o.getId().toString(), // Ensure this matches UUID in your class
                            o.getCustomerId() != null ? "Cust: " + o.getCustomerId().toString().substring(0, 8) : "Guest",
                            o.getStatus() != null ? o.getStatus().name() : "UNKNOWN",
                            o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0,
                            o.getCreatedAt() != null ? o.getCreatedAt().toString() : "N/A"
                    );
                })
                .collect(Collectors.toList());
    }
}