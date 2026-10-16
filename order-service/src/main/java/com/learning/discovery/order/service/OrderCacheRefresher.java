package com.learning.discovery.order.service;

import com.learning.discovery.order.model.Order;
import com.learning.discovery.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Reloads every order from Postgres into the "orders" cache (key = order id). Runs once at
 * startup, then every 5 minutes. Entries expire 10 minutes after they are written (Caffeine
 * spec in application.yml), so an order deleted from the DB disappears from the cache within
 * two refresh cycles. Visible in Spring Boot Admin under Scheduled Tasks and Caches.
 */
@Slf4j
@Component
class OrderCacheRefresher {

    private final OrderRepository orderRepository;
    private final Cache ordersCache;

    OrderCacheRefresher(OrderRepository orderRepository, CacheManager cacheManager) {
        this.orderRepository = orderRepository;
        this.ordersCache = Objects.requireNonNull(cacheManager.getCache(OrderService.ORDERS_CACHE));
    }

    @Scheduled(fixedRateString = "${orders.cache.refresh-interval:5m}")
    void refreshOrdersCache() {
        List<Order> orders = orderRepository.findAll();
        orders.forEach(order -> ordersCache.put(order.id(), order));
        log.info("Refreshed '{}' cache with {} orders", OrderService.ORDERS_CACHE, orders.size());
    }
}
