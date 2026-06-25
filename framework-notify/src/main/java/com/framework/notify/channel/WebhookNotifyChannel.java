package com.framework.notify.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.notify.config.NotifyProperties;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Webhook notification channel based on JDK HttpClient.
 */
@Slf4j
public class WebhookNotifyChannel implements NotifyChannel {

    private final NotifyProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebhookNotifyChannel(NotifyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getWebhook().getTimeout())
                .build();
    }

    @Override
    public NotifyChannelType type() {
        return NotifyChannelType.WEBHOOK;
    }

    @Override
    public NotifyResult send(NotifyMessage message) {
        String url = StringUtils.hasText(message.getWebhookUrl())
                ? message.getWebhookUrl()
                : properties.getWebhook().getUrl();
        if (!StringUtils.hasText(url)) {
            return NotifyResult.failure(type(), "webhook url is empty");
        }

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "title", message.getTitle(),
                    "content", message.getContent(),
                    "receivers", message.getReceivers(),
                    "templateParams", message.getTemplateParams()
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(properties.getWebhook().getTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return NotifyResult.success(type());
            }
            return NotifyResult.failure(type(), "webhook status=" + response.statusCode());
        } catch (Exception e) {
            log.warn("[Webhook通知失败] title={}, error={}", message.getTitle(), e.getMessage());
            return NotifyResult.failure(type(), e.getMessage());
        }
    }
}
