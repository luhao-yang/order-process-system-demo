package com.example.orders.common.error;

import com.example.orders.order.entity.OrderStatus;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(OrderStatus from, OrderStatus to) {
        super("Invalid status transition: " + from + " -> " + to);
    }
}
