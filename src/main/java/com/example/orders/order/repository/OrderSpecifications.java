package com.example.orders.order.repository;

import com.example.orders.order.entity.Order;
import com.example.orders.order.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, q, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasCustomerId(String customerId) {
        return (root, q, cb) -> (customerId == null || customerId.isBlank())
                ? null
                : cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Order> createdFrom(Instant from) {
        return (root, q, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdTo(Instant to) {
        return (root, q, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
