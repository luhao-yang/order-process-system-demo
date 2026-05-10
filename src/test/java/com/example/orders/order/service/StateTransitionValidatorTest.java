package com.example.orders.order.service;

import com.example.orders.common.error.InvalidStateTransitionException;
import com.example.orders.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StateTransitionValidatorTest {

    private final StateTransitionValidator validator = new StateTransitionValidator();

    @Test
    void allowsCreatedToCompleted() {
        assertDoesNotThrow(() -> validator.validate(OrderStatus.CREATED, OrderStatus.COMPLETED));
    }

    @Test
    void allowsCreatedToCancelled() {
        assertDoesNotThrow(() -> validator.validate(OrderStatus.CREATED, OrderStatus.CANCELLED));
    }

    @Test
    void rejectsSameState() {
        assertThrows(InvalidStateTransitionException.class,
                () -> validator.validate(OrderStatus.CREATED, OrderStatus.CREATED));
    }

    @Test
    void rejectsFromTerminalCompleted() {
        assertThrows(InvalidStateTransitionException.class,
                () -> validator.validate(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
    }

    @Test
    void rejectsFromTerminalCancelled() {
        assertThrows(InvalidStateTransitionException.class,
                () -> validator.validate(OrderStatus.CANCELLED, OrderStatus.COMPLETED));
    }
}
