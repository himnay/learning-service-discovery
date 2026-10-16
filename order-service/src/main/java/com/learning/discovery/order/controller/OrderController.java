package com.learning.discovery.order.controller;

import com.learning.discovery.order.client.UserClient;
import com.learning.discovery.order.model.Order;
import com.learning.discovery.order.model.OrderWithUser;
import com.learning.discovery.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
class OrderController {

    private final OrderService orderService;
    private final UserClient userClient;

    OrderController(OrderService orderService, UserClient userClient) {
        this.orderService = orderService;
        this.userClient = userClient;
    }

    @GetMapping("/api/orders")
    List<Order> getOrders() {
        return orderService.findAll();
    }

    @GetMapping("/api/orders/{id}")
    Order getOrder(@PathVariable Long id) {
        return orderService.findById(id)
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
