package com.example.orders.order.dto;

import com.example.orders.notification.dto.NotificationOutcome;

import java.util.List;

public record OrderWithNotificationsResponse(OrderResponse order, List<NotificationOutcome> notifications) {}
