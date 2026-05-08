package com.scm.domains.orders.controllers;

import com.scm.domains.orders.dtos.OrderDTO;
import com.scm.domains.orders.services.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger _LOGGER = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
        _LOGGER.info("OrderController initialized.");
    }

    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(@Valid @RequestBody OrderDTO orderDto) {
        _LOGGER.info("Received REST request to create order: {}", orderDto.orderNumber());
        OrderDTO createdOrder = orderService.createOrder(orderDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable UUID id) {
        _LOGGER.info("Received REST request to fetch order: {}", id);
        return ResponseEntity.ok(orderService.getOrder(id));
    }
}