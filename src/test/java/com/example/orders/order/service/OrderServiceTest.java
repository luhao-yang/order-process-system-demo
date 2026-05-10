package com.example.orders.order.service;

import com.example.orders.common.error.OrderNotFoundException;
import com.example.orders.notification.dto.NotificationOutcome;
import com.example.orders.notification.api.NotificationService;
import com.example.orders.order.entity.Order;
import com.example.orders.order.entity.OrderItem;
import com.example.orders.order.entity.OrderStatus;
import com.example.orders.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository repository;
    @Mock NotificationService notifications;
    StateTransitionValidator validator = new StateTransitionValidator();

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(repository, validator, notifications);
    }

    @Test
    void createPersistsAndDispatchesNotifications() {
        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        List<NotificationOutcome> outcomes = List.of(NotificationOutcome.sent("email"));
        when(notifications.notify(any(UUID.class), eq("c-1"), eq("ORDER_CREATED"), eq("CREATED")))
                .thenReturn(outcomes);

        OrderResult result = service.create("c-1", List.of(new OrderItem("p-1", 2, new BigDecimal("9.99"))));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.order().getTotalAmount()).isEqualByComparingTo("19.98");
        assertThat(result.notifications()).isSameAs(outcomes);
    }

    @Test
    void getThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void updateStatusValidatesAndDispatches() {
        Order existing = Order.create("c-1", List.of(new OrderItem("p-1", 1, BigDecimal.ONE)));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.notify(eq(existing.getId()), eq("c-1"), eq("ORDER_STATUS_CHANGED"), eq("COMPLETED")))
                .thenReturn(List.of());

        OrderResult result = service.updateStatus(existing.getId(), OrderStatus.COMPLETED);

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(notifications).notify(existing.getId(), "c-1", "ORDER_STATUS_CHANGED", "COMPLETED");
    }
}
