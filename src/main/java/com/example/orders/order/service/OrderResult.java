package com.example.orders.order.service;

import com.example.orders.notification.dto.NotificationOutcome;
import com.example.orders.order.entity.Order;

import java.util.List;

public record OrderResult(Order order, List<NotificationOutcome> notifications) {}
