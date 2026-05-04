package com.scm.domains.inventory.services;

import com.scm.domains.inventory.dtos.StockSummaryDTO;
import com.scm.domains.inventory.mappers.InventoryMapper;
import com.scm.exceptions.domains.inventory.InvalidOperationException;
import com.scm.exceptions.domains.inventory.ResourceNotFoundException;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.scm.domains.inventory.generated.Tables.*;
import static org.jooq.impl.DSL.sum;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    @Autowired
    private DSLContext dsl;

    @Autowired
    private InventoryMapper inventoryMapper;

    /**
     * Aggregates total stock per product across all warehouses,
     * now including Category information.
     */
    public List<StockSummaryDTO> getGlobalStockSummary() {
        return dsl.select(
                        PRODUCTS.SKU,
                        PRODUCTS.NAME,
                        CATEGORIES.NAME.as("category"),
                        sum(STOCK.QUANTITY).as("total_quantity")
                )
                .from(PRODUCTS)
                .join(CATEGORIES).on(PRODUCTS.CATEGORY_ID.eq(CATEGORIES.ID))
                .leftJoin(STOCK).on(PRODUCTS.ID.eq(STOCK.PRODUCT_ID))
                .groupBy(PRODUCTS.SKU, PRODUCTS.NAME, CATEGORIES.NAME)
                .fetchInto(StockSummaryDTO.class); // jOOQ can map directly to Records!
    }

    /**
     * Highly specific filter for Category-based Master-Detail views.
     */
    public List<?> getProductsByCategory(Integer categoryId) {
        var results = dsl.selectFrom(PRODUCTS)
                .where(PRODUCTS.CATEGORY_ID.eq(categoryId))
                .fetchMaps();

        if (results.isEmpty()) {
            throw new ResourceNotFoundException("Category", categoryId);
        }
        return results;
    }

    @Transactional
    public void processMovement(Integer productId, Integer warehouseId, Integer change, String type, String note) {
        // Pessimistic Lock
        var stockRecord = dsl.selectFrom(STOCK)
                .where(STOCK.PRODUCT_ID.eq(productId))
                .and(STOCK.WAREHOUSE_ID.eq(warehouseId))
                .forUpdate()
                .fetchOne();

        if (stockRecord == null) {
            // Replaced generic RuntimeException
            throw new ResourceNotFoundException("Stock Record", "P:" + productId + "-W:" + warehouseId);
        }

        int resultQty = stockRecord.getQuantity() + change;

        if (resultQty < 0) {
            // Replaced IllegalArgumentException with specific Business Exception
            throw new InvalidOperationException(
                    String.format("Insufficient stock. Available: %d, Requested change: %d", stockRecord.getQuantity(), change),
                    "INVENTORY_INSUFFICIENT_STOCK"
            );
        }

        stockRecord.setQuantity(resultQty);
        stockRecord.store();

        // Audit Trail
        dsl.insertInto(STOCK_MOVEMENTS)
                .set(STOCK_MOVEMENTS.PRODUCT_ID, productId)
                .set(STOCK_MOVEMENTS.WAREHOUSE_ID, warehouseId)
                .set(STOCK_MOVEMENTS.CHANGE_AMOUNT, change)
                .set(STOCK_MOVEMENTS.MOVEMENT_TYPE, type)
                .set(STOCK_MOVEMENTS.REMARKS, note)
                .execute();

        log.warn("SCM Transaction Complete: {} | Product ID: {} | New Balance: {}", type, productId, resultQty);
    }

    public List<?> getStockByWarehouse() {
        return dsl.select(
                        WAREHOUSES.NAME.as("warehouse"),
                        CITIES.NAME.as("city"),
                        COUNTRIES.NAME.as("country"),
                        PRODUCTS.SKU,
                        PRODUCTS.NAME.as("product_name"),
                        STOCK.QUANTITY
                )
                .from(STOCK)
                .join(PRODUCTS).on(STOCK.PRODUCT_ID.eq(PRODUCTS.ID))
                .join(WAREHOUSES).on(STOCK.WAREHOUSE_ID.eq(WAREHOUSES.ID))
                .join(LOCATIONS).on(WAREHOUSES.LOCATION_ID.eq(LOCATIONS.ID))
                .join(CITIES).on(LOCATIONS.CITY_ID.eq(CITIES.ID))
                .join(COUNTRIES).on(CITIES.COUNTRY_ID.eq(COUNTRIES.ID))
                .fetchMaps();
    }

    public List<?> getSuppliersForProduct(Integer productId) {
        return dsl.select(
                        SUPPLIERS.NAME.as("supplier_name"),
                        CITIES.NAME.as("city"),
                        COUNTRIES.NAME.as("country"),
                        SUPPLIER_PRODUCTS.SUPPLY_PRICE,
                        SUPPLIER_PRODUCTS.LEAD_TIME_DAYS
                )
                .from(SUPPLIER_PRODUCTS)
                .join(SUPPLIERS).on(SUPPLIER_PRODUCTS.SUPPLIER_ID.eq(SUPPLIERS.ID))
                .join(LOCATIONS).on(SUPPLIERS.LOCATION_ID.eq(LOCATIONS.ID))
                .join(CITIES).on(LOCATIONS.CITY_ID.eq(CITIES.ID))
                .join(COUNTRIES).on(CITIES.COUNTRY_ID.eq(COUNTRIES.ID))
                .where(SUPPLIER_PRODUCTS.PRODUCT_ID.eq(productId))
                .orderBy(SUPPLIER_PRODUCTS.SUPPLY_PRICE.asc())
                .fetchMaps();
    }

    public List<?> getProductMovementHistory(Integer productId) {
        var query = dsl.select(
                        STOCK_MOVEMENTS.CREATED_AT,
                        STOCK_MOVEMENTS.MOVEMENT_TYPE,
                        STOCK_MOVEMENTS.CHANGE_AMOUNT,
                        WAREHOUSES.NAME.as("warehouse_name"),
                        STOCK_MOVEMENTS.REMARKS
                )
                .from(STOCK_MOVEMENTS)
                .join(WAREHOUSES).on(STOCK_MOVEMENTS.WAREHOUSE_ID.eq(WAREHOUSES.ID))
                .where(STOCK_MOVEMENTS.PRODUCT_ID.eq(productId))
                .orderBy(STOCK_MOVEMENTS.CREATED_AT.desc());

        log.warn("Fetching movement history for Product ID: {}", productId);
        return query.fetchMaps();
    }
}