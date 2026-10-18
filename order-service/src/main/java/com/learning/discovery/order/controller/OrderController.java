package com.learning.discovery.order.controller;

import com.learning.discovery.order.client.UserClient;
import com.learning.discovery.order.model.Order;
import com.learning.discovery.order.model.OrderWithUser;
import com.learning.discovery.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@ApiResponse(responseCode = "401", description = "Missing or wrong HTTP Basic credentials", content = @Content)
@Tag(name = "Orders", description = "Read-only orders (Postgres + Caffeine cache), optionally joined with user-service")
class OrderController {

    private final OrderService orderService;
    private final UserClient userClient;

    OrderController(OrderService orderService, UserClient userClient) {
        this.orderService = orderService;
        this.userClient = userClient;
    }

    @GetMapping("/api/orders")
    @Operation(summary = "List all orders")
    @ApiResponse(responseCode = "200", description = "All orders")
    List<Order> getOrders() {
        return orderService.findAll();
    }

    @Operation(summary = "Get an order by id", description = "Cached per id in Caffeine (`orders` cache).")
    @ApiResponse(responseCode = "200", description = "The order")
    @ApiResponse(responseCode = "404", description = "No order with this id",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/orders/{id}")
    Order getOrder(@Parameter(description = "Order id", example = "101") @PathVariable Long id) {
        return orderService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + id + " not found"));
    }

    // Demonstrates a direct, load-balanced service-to-service call: user-service is
    // resolved via Eureka + Spring Cloud LoadBalancer, not routed through gateway-service.
    @Operation(summary = "Get an order with its user",
            description = "Calls user-service directly (Eureka + Spring Cloud LoadBalancer, no gateway hop). "
                    + "`user` is null when user-service is down or does not know the user.")
    @ApiResponse(responseCode = "200", description = "The order and its user")
    @ApiResponse(responseCode = "404", description = "No order with this id",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/orders/{id}/with-user")
    OrderWithUser getOrderWithUser(@Parameter(description = "Order id", example = "101") @PathVariable Long id) {
        Order order = getOrder(id);
        return new OrderWithUser(order, userClient.fetchUser(order.userId()).orElse(null));
    }
}
