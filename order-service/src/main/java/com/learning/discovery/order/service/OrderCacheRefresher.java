package com.learning.discovery.order.service;

import com.learning.discovery.order.model.Order;
import com.learning.discovery.order.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reloads every order from Postgres into the "orders" cache (key = order id). Runs once at
 * startup, then every 5 minutes. Entries expire 10 minutes after they are written (Caffeine
 * spec in application.yml), so an order deleted from the DB disappears from the cache within
 * two refresh cycles. Visible in Spring Boot Admin under Scheduled Tasks and Caches; each run is
 * timed as the {@code orders.cache.refresh} metric and reported by {@link OrdersCacheHealthIndicator}.
 */
@Slf4j
@Component
class OrderCacheRefresher {

    private final OrderRepository orderRepository;
    private final Cache ordersCache;
    private final Timer refreshTimer;
    private volatile Instant lastRefresh;
    private volatile int lastRefreshCount;

    OrderCacheRefresher(OrderRepository orderRepository, CacheManager cacheManager, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.ordersCache = Objects.requireNonNull(cacheManager.getCache(OrderService.ORDERS_CACHE));
        this.refreshTimer = Timer.builder("orders.cache.refresh")
                .description("Time to reload all orders from Postgres into the orders cache")
                .register(meterRegistry);
    }

    @Scheduled(fixedRateString = "${orders.cache.refresh-interval:5m}")
    void refreshOrdersCache() {
        refreshTimer.record(() -> {
            List<Order> orders = orderRepository.findAll();
            orders.forEach(order -> ordersCache.put(order.id(), order));
            lastRefreshCount = orders.size();
            lastRefresh = Instant.now();
            log.info("Refreshed '{}' cache with {} orders", OrderService.ORDERS_CACHE, orders.size());
        });
    }

    Optional<Instant> lastRefresh() {
        return Optional.ofNullable(lastRefresh);
    }

    int lastRefreshCount() {
        return lastRefreshCount;
    }
}
