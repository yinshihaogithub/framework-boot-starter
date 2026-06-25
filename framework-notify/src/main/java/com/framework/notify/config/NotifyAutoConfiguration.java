package com.framework.notify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.notify.channel.LogNotifyChannel;
import com.framework.notify.channel.NotifyChannel;
import com.framework.notify.channel.WebhookNotifyChannel;
import com.framework.notify.service.DefaultNotifyService;
import com.framework.notify.service.NotifyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Notify module auto configuration.
 */
@Configuration
@EnableConfigurationProperties(NotifyProperties.class)
@ConditionalOnProperty(prefix = "framework.notify", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper notifyObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public LogNotifyChannel logNotifyChannel() {
        return new LogNotifyChannel();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookNotifyChannel webhookNotifyChannel(NotifyProperties properties, ObjectMapper objectMapper) {
        return new WebhookNotifyChannel(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyService notifyService(NotifyProperties properties, List<NotifyChannel> channels) {
        return new DefaultNotifyService(properties, channels);
    }
}
