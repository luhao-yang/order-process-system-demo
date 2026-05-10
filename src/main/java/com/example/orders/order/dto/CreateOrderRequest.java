package com.example.orders.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<OrderItemRequest> items
) {
    public record OrderItemRequest(
            @NotBlank String productId,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice
    ) {}
}
