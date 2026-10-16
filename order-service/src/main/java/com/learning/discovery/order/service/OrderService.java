package com.learning.discovery.order.service;

import com.learning.discovery.order.model.Order;
import com.learning.discovery.order.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    public static final String ORDERS_CACHE = "orders";

    private final OrderRepository orderRepository;
    // orders.db.reads{op}: Postgres reads only — findById cache hits never get here, so
    // op=findById vs. the request count shows how well the cache works (SBA Metrics).
    private final Counter listReads;
    private final Counter findByIdReads;

    OrderService(OrderRepository orderRepository, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.listReads = dbReads(meterRegistry, "findAll");
        this.findByIdReads = dbReads(meterRegistry, "findById");
    }

    private static Counter dbReads(MeterRegistry meterRegistry, String op) {
        return Counter.builder("orders.db.reads")
                .description("Order reads that reached Postgres")
                .tag("op", op)
                .register(meterRegistry);
    }

    public List<Order> findAll() {
        listReads.increment();
        return orderRepository.findAll();
    }

    // Served from the "orders" cache (warmed by OrderCacheRefresher); a miss falls through
    // to Postgres and the result is cached under the same id key.
    @Cacheable(cacheNames = ORDERS_CACHE, unless = "#result == null")
    public Optional<Order> findById(Long id) {
        findByIdReads.increment();
        return orderRepository.findById(id);
    }
}
