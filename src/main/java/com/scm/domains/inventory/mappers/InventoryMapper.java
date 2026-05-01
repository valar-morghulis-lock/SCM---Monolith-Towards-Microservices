package com.scm.domains.inventory.mappers;

import com.scm.domains.inventory.dtos.StockSummaryDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface InventoryMapper {

    @Mapping(target = "sku", source = "SKU")
    @Mapping(target = "name", source = "NAME")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "totalQuantity", source = "total_quantity")
    StockSummaryDTO toStockSummaryDTO(Map<String, Object> map);

    List<StockSummaryDTO> toStockSummaryDTOList(List<Map<String, Object>> list);

    /**
     * Helper to bridge jOOQ Map Objects to String
     */
    default String mapToString(Object value) {
        return value != null ? value.toString() : null;
    }

    /**
     * Helper to bridge jOOQ Map Objects to BigDecimal
     */
    default BigDecimal mapToBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }
}