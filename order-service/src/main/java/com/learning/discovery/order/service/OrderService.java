package com.learning.discovery.order.service;

import com.learning.discovery.order.model.Order;
import com.learning.discovery.order.repository.OrderRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    public static final String ORDERS_CACHE = "orders";

    private final OrderRepository orderRepository;

    OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    // Served from the "orders" cache (warmed by OrderCacheRefresher); a miss falls through
    // to Postgres and the result is cached under the same id key.
    @Cacheable(cacheNames = ORDERS_CACHE, unless = "#result == null")
    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }
}
