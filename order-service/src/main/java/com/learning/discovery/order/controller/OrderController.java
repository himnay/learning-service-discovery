package com.learning.discovery.order.controller;

import com.learning.discovery.order.model.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
class OrderController {

    private static final List<Order> ORDERS = List.of(
            new Order(101L, 1L, "Keyboard", 1),
            new Order(102L, 2L, "Monitor", 2),
            new Order(103L, 1L, "Mouse", 3));

    @GetMapping("/api/orders")
    List<Order> getOrders() {
        return ORDERS;
    }

    @GetMapping("/api/orders/{id}")
    Order getOrder(@PathVariable Long id) {
        return ORDERS.stream()
                .filter(order -> order.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + id + " not found"));
    }
}
