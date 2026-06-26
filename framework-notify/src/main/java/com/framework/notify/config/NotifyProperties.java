package com.framework.notify.config;

import com.framework.notify.model.NotifyChannelType;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Notify module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.notify")
public class NotifyProperties implements InitializingBean {

    private boolean enabled = true;
    private NotifyChannelType defaultChannel = NotifyChannelType.LOG;
    private Webhook webhook = new Webhook();

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    public void validate() {
        if (defaultChannel == null) {
            throw new IllegalArgumentException("framework.notify.default-channel must not be null");
        }
        if (webhook == null) {
            throw new IllegalArgumentException("framework.notify.webhook must not be null");
        }
        if (webhook.getTimeout() == null || webhook.getTimeout().isZero() || webhook.getTimeout().isNegative()) {
            throw new IllegalArgumentException("framework.notify.webhook.timeout must be greater than 0");
        }
        validateWebhookUrl(webhook.getUrl());
    }

    private void validateWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("framework.notify.webhook.url must be a valid URI", e);
        }
        if (!isHttpUri(uri)) {
            throw new IllegalArgumentException("framework.notify.webhook.url must use http or https");
        }
    }

    private boolean isHttpUri(URI uri) {
        String scheme = uri.getScheme();
        return uri.getHost() != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }

    @Data
    public static class Webhook {
        private String url = "";
        private Duration timeout = Duration.ofSeconds(3);
    }
}
