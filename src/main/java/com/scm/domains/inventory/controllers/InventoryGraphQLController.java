package com.scm.domains.inventory.controllers;

import com.scm.domains.dashboard.dtos.StockSummary;
import com.scm.domains.inventory.repositories.InventoryDashboardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class InventoryGraphQLController {

    private static final Logger log = LoggerFactory.getLogger(InventoryGraphQLController.class);
    private final InventoryDashboardRepository inventoryDashboardRepository;

    public InventoryGraphQLController(InventoryDashboardRepository inventoryDashboardRepository) {
        this.inventoryDashboardRepository = inventoryDashboardRepository;
    }

    @QueryMapping // Maps directly to the 'globalStockSummary' query declared in dashboard.graphqls
    public List<StockSummary> globalStockSummary() {
        log.warn("GRAPHQL QUERY: Fetching global stock summaries via jOOQ engine.");
        return inventoryDashboardRepository.getGlobalStockSummary();
    }
}