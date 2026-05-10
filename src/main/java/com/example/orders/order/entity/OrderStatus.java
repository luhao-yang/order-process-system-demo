package com.example.orders.order.entity;

public enum OrderStatus {
    CREATED,
    CANCELLED,
    COMPLETED;

    public boolean isTerminal() {
        return this == CANCELLED || this == COMPLETED;
    }
}
