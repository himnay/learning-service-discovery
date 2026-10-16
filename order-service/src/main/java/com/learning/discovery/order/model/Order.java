package com.learning.discovery.order.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

// "order" is a reserved SQL word, hence the plural table name.
@Table("orders")
public record Order(@Id Long id, Long userId, String product, int quantity) {
}
