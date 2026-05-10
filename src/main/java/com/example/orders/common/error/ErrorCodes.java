package com.example.orders.common.error;

public final class ErrorCodes {
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ORDER_INVALID_STATE_TRANSITION = "ORDER_INVALID_STATE_TRANSITION";
    public static final String ORDER_CONCURRENT_MODIFICATION = "ORDER_CONCURRENT_MODIFICATION";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {}
}
