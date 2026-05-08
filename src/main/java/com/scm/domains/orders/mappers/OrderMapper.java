package com.scm.domains.orders.mappers;


import com.scm.domains.orders.dtos.OrderDTO;
import com.scm.domains.orders.dtos.OrderItemDTO;
import com.scm.domains.orders.entities.Order;
import com.scm.domains.orders.entities.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    // Maps the Entity to a DTO for API responses
    OrderDTO toDto(Order order);

    // Maps a child Entity to a child DTO
    OrderItemDTO toItemDto(OrderItem item);

    // Maps an incoming Request/DTO to an Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "items", ignore = true) // Handled manually in service to link parent
    Order toEntity(OrderDTO dto);
}