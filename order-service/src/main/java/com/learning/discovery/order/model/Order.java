package com.learning.discovery.order.model;

public record Order(Long id, Long userId, String product, int quantity) {
}
