package com.framework.notify.config;

import com.framework.notify.model.NotifyChannelType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Notify module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.notify")
public class NotifyProperties {

    private boolean enabled = true;
    private NotifyChannelType defaultChannel = NotifyChannelType.LOG;
    private Webhook webhook = new Webhook();

    @Data
    public static class Webhook {
        private String url = "";
        private Duration timeout = Duration.ofSeconds(3);
    }
}
