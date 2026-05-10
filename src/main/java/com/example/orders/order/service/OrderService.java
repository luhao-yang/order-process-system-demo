package com.example.orders.order.service;

import com.example.orders.common.error.OrderNotFoundException;
import com.example.orders.notification.api.NotificationService;
import com.example.orders.notification.dto.NotificationOutcome;
import com.example.orders.order.entity.*;
import com.example.orders.order.repository.OrderRepository;
import com.example.orders.order.repository.OrderSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final StateTransitionValidator validator;
    private final NotificationService notifications;

    public OrderService(OrderRepository repository,
                        StateTransitionValidator validator,
                        NotificationService notifications) {
        this.repository = repository;
        this.validator = validator;
        this.notifications = notifications;
    }

    @Transactional
    public OrderResult create(String customerId, List<OrderItem> items) {
        Order saved = repository.save(Order.create(customerId, items));
        log.info("Order {} created for customer {}", saved.getId(), saved.getCustomerId());
        List<NotificationOutcome> outcomes = notifications.notify(
                saved.getId(), saved.getCustomerId(), "ORDER_CREATED", saved.getStatus().name());
        return new OrderResult(saved, outcomes);
    }

    @Transactional
    public OrderResult updateStatus(UUID id, OrderStatus next) {
        Order order = repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        OrderStatus previous = order.getStatus();
        validator.validate(previous, next);
        order.changeStatus(next);
        Order saved = repository.save(order);
        log.info("Order {} status: {} -> {}", saved.getId(), previous, next);
        List<NotificationOutcome> outcomes = notifications.notify(
                saved.getId(), saved.getCustomerId(), "ORDER_STATUS_CHANGED", saved.getStatus().name());
        return new OrderResult(saved, outcomes);
    }

    @Transactional(readOnly = true)
    public Order get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Order> search(OrderStatus status, String customerId, Instant from, Instant to, Pageable pageable) {
        Specification<Order> spec = Specification
                .where(OrderSpecifications.hasStatus(status))
                .and(OrderSpecifications.hasCustomerId(customerId))
                .and(OrderSpecifications.createdFrom(from))
                .and(OrderSpecifications.createdTo(to));
        return repository.findAll(spec, pageable);
    }
}
