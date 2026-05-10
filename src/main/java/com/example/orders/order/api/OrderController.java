package com.example.orders.order.api;

import com.example.orders.notification.dto.NotificationOutcome;
import com.example.orders.order.entity.OrderItem;
import com.example.orders.order.entity.OrderStatus;
import com.example.orders.order.dto.CreateOrderRequest;
import com.example.orders.order.dto.OrderResponse;
import com.example.orders.order.dto.OrderWithNotificationsResponse;
import com.example.orders.order.dto.UpdateStatusRequest;
import com.example.orders.order.service.OrderResult;
import com.example.orders.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderWithNotificationsResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        List<OrderItem> items = req.items().stream()
                .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
                .toList();
        return wrap(service.create(req.customerId(), items), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(service.get(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderWithNotificationsResponse> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest req) {
        return wrap(service.updateStatus(id, req.status()), HttpStatus.OK);
    }

    @GetMapping
    public Page<OrderResponse> search(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            Pageable pageable) {
        return service.search(status, customerId, createdFrom, createdTo, pageable).map(OrderResponse::from);
    }

    private ResponseEntity<OrderWithNotificationsResponse> wrap(OrderResult result, HttpStatus successStatus) {
        List<NotificationOutcome> outcomes = result.notifications();
        boolean anyFailed = outcomes.stream().anyMatch(NotificationOutcome::isFailed);
        HttpStatus status = anyFailed ? HttpStatus.MULTI_STATUS : successStatus;
        return ResponseEntity.status(status)
                .body(new OrderWithNotificationsResponse(OrderResponse.from(result.order()), outcomes));
    }
}
