package com.example.orders.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "notifications")
public class NotificationProperties {

    private String url;
    private Map<String, ChannelConfig> channels = new HashMap<>();

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Map<String, ChannelConfig> getChannels() { return channels; }
    public void setChannels(Map<String, ChannelConfig> channels) { this.channels = channels; }

    public static class ChannelConfig {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
