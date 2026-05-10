package com.example.orders.notification.dto;

public record NotificationOutcome(String channel, Status status, String errorCode, String message) {
    public enum Status { SENT, FAILED }

    public static NotificationOutcome sent(String channel) {
        return new NotificationOutcome(channel, Status.SENT, null, null);
    }

    public static NotificationOutcome failed(String channel, String errorCode, String message) {
        return new NotificationOutcome(channel, Status.FAILED, errorCode, message);
    }

    public boolean isFailed() { return status == Status.FAILED; }
}
