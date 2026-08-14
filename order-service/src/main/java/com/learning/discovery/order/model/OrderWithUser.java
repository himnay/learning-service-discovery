package com.learning.discovery.order.model;

public record OrderWithUser(Order order, UserSummary user) {
}
