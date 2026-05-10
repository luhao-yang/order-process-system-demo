package com.example.orders.notification.api;

import com.example.orders.notification.config.NotificationProperties;
import com.example.orders.notification.dto.NotificationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RestClient restClient;
    private final NotificationProperties props;
    private final NotificationService self;

    public NotificationService(RestClient notificationRestClient,
                               NotificationProperties props,
                               @Lazy NotificationService self) {
        this.restClient = notificationRestClient;
        this.props = props;
        this.self = self;
    }

    public List<NotificationOutcome> notify(UUID orderId, String customerId, String eventType, String status) {
        Payload payload = new Payload(orderId, customerId, eventType, status);
        String base = props.getUrl().endsWith("/") ? props.getUrl() : props.getUrl() + "/";
        List<NotificationOutcome> outcomes = new ArrayList<>();
        for (Map.Entry<String, NotificationProperties.ChannelConfig> e : props.getChannels().entrySet()) {
            if (e.getValue() == null || !e.getValue().isEnabled()) continue;
            String channel = e.getKey();
            outcomes.add(self.send(channel, base + channel, payload));
        }
        return outcomes;
    }

    @Retryable(
            retryFor = RestClientException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2.0))
    public NotificationOutcome send(String channel, String url, Payload payload) {
        log.info("Sending {} notification for order {} (event={})", channel, payload.orderId(), payload.eventType());
        restClient.post().uri(url).body(payload).retrieve().toBodilessEntity();
        return NotificationOutcome.sent(channel);
    }


    // @Recover is Spring Retry's "fallback handler" — the method that runs when a @Retryable method has used all its attempts and is about to throw. Instead of letting the
    //  exception propagate, Spring Retry routes the call to the matching @Recover method and uses its return value as the result of the original call.
    @Recover
    public NotificationOutcome recover(RestClientException ex, String channel, String url, Payload payload) {
        log.warn("Channel {} exhausted retries for order {}: {}", channel, payload.orderId(), ex.getMessage());
        return NotificationOutcome.failed(channel,
                "NOTIF_" + channel.toUpperCase() + "_FAILED", ex.getMessage());
    }

    public record Payload(UUID orderId, String customerId, String eventType, String status) {}
}
