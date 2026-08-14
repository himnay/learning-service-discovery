package com.learning.discovery.order.controller;

import com.learning.discovery.order.client.UserClient;
import com.learning.discovery.order.model.Order;
import com.learning.discovery.order.model.OrderWithUser;
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

    private final UserClient userClient;

    OrderController(UserClient userClient) {
        this.userClient = userClient;
    }

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

    // Demonstrates a direct, load-balanced service-to-service call: user-service is
    // resolved via Eureka + Spring Cloud LoadBalancer, not routed through gateway-service.
    @GetMapping("/api/orders/{id}/with-user")
    OrderWithUser getOrderWithUser(@PathVariable Long id) {
        Order order = getOrder(id);
        return new OrderWithUser(order, userClient.fetchUser(order.userId()).orElse(null));
    }
}
