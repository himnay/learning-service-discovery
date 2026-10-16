package com.learning.discovery.order.service;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * "ordersCache" in /actuator/health (SBA Details → Health). UP once OrderCacheRefresher has
 * loaded the cache; UNKNOWN before the first run or when the cache is empty — UNKNOWN never
 * drags the aggregate status down, an empty cache only means every read goes to Postgres.
 */
@Component
class OrdersCacheHealthIndicator implements HealthIndicator {

    private final OrderCacheRefresher refresher;
    private final Cache ordersCache;

    OrdersCacheHealthIndicator(OrderCacheRefresher refresher, CacheManager cacheManager) {
        this.refresher = refresher;
        this.ordersCache = Objects.requireNonNull(cacheManager.getCache(OrderService.ORDERS_CACHE));
    }

    @Override
    public Health health() {
        long entries = ((com.github.benmanes.caffeine.cache.Cache<?, ?>) ordersCache.getNativeCache()).estimatedSize();
        Health.Builder builder = entries > 0 ? Health.up() : Health.unknown();
        builder.withDetail("entries", entries);
        refresher.lastRefresh().ifPresentOrElse(
                at -> builder.withDetail("lastRefresh", at.toString())
                        .withDetail("lastRefreshCount", refresher.lastRefreshCount()),
                () -> builder.withDetail("lastRefresh", "not yet run"));
        return builder.build();
    }
}
