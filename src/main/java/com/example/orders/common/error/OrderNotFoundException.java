package com.example.orders.common.error;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID id) {
        super("Order " + id + " not found");
    }
}
