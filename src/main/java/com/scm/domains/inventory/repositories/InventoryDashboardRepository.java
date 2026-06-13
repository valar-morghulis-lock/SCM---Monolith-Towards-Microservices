package com.scm.domains.inventory.repositories;

import com.scm.domains.dashboard.dtos.StockSummary;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.scm.domains.inventory.generated.tables.Stock.STOCK;

@Repository
public class InventoryDashboardRepository {

    private final DSLContext dsl;

    public InventoryDashboardRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Compiles aggregate global inventory analytics using accurate jOOQ table fields.
     */
    public List<StockSummary> getGlobalStockSummary() {
        return dsl.select(
                        STOCK.PRODUCT_ID,
                        STOCK.products().NAME, // Maps straight to public.products.name
                        STOCK.products().SKU,  // Maps straight to public.products.sku
                        DSL.sum(STOCK.QUANTITY).cast(Integer.class).as("totalQuantity"),
                        DSL.countDistinct(STOCK.WAREHOUSE_ID).as("warehouseCount")
                )
                .from(STOCK)
                .groupBy(STOCK.PRODUCT_ID, STOCK.products().NAME, STOCK.products().SKU)
                .fetchInto(StockSummary.class);
    }
}