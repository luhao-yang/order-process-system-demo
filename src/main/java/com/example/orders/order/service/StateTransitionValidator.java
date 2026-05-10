package com.example.orders.order.service;

import com.example.orders.common.error.InvalidStateTransitionException;
import com.example.orders.order.entity.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class StateTransitionValidator {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.CREATED, Set.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.COMPLETED, Set.of()
    );

    public void validate(OrderStatus from, OrderStatus to) {
        if (from == to || !ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }
}
