package com.learning.discovery.order.repository;

import com.learning.discovery.order.model.Order;
import org.springframework.data.repository.ListCrudRepository;

public interface OrderRepository extends ListCrudRepository<Order, Long> {
}
