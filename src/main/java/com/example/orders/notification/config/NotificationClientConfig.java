package com.example.orders.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class NotificationClientConfig {

    /**
     * Force HTTP/1.1 — WireMock occasionally aborts HTTP/2 streams (RST_STREAM)
     * when stub matching changes during retries, which is hard to reason about in tests.
     */
    @Bean
    public RestClient notificationRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }
}
