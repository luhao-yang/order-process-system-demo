package com.example.orders.security.dto;

public record AuthResponse(String accessToken, long expiresIn) {}
