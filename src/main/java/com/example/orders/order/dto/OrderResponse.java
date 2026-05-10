package com.example.orders.order.dto;

import com.example.orders.order.entity.Order;
import com.example.orders.order.entity.OrderItem;
import com.example.orders.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public record Item(String productId, int quantity, BigDecimal unitPrice) {
        static Item from(OrderItem i) { return new Item(i.getProductId(), i.getQuantity(), i.getUnitPrice()); }
    }

    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(),
                o.getCustomerId(),
                o.getStatus(),
                o.getTotalAmount(),
                o.getItems().stream().map(Item::from).toList(),
                o.getCreatedAt(),
                o.getUpdatedAt(),
                o.getVersion()
        );
    }
}
