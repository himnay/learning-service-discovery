package com.learning.discovery.product.model;

import java.math.BigDecimal;

public record Product(Long id, String name, BigDecimal price) {
}
