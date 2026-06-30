package com.framework.notify.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import com.framework.notify.config.NotifyProperties;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import com.framework.notify.support.NotifyTextSupport;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Webhook notification channel based on JDK HttpClient.
 */
@Slf4j
public class WebhookNotifyChannel implements NotifyChannel {

    private final NotifyProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebhookNotifyChannel(NotifyProperties properties, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "notify properties must not be null");
        this.properties.validate();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout())
                .build();
    }

    @Override
    public NotifyChannelType type() {
        return NotifyChannelType.WEBHOOK;
    }

    @Override
    public NotifyResult send(NotifyMessage message) {
        NotifyResult validationResult = validateMessage(message);
        if (validationResult != null) {
            return validationResult;
        }
        String url = NotifyTextSupport.hasText(message.getWebhookUrl())
                ? NotifyTextSupport.trimBoundarySpace(message.getWebhookUrl())
                : NotifyTextSupport.trimToNull(properties.getWebhook().getUrl());
        if (!NotifyTextSupport.hasText(url)) {
            return NotifyResult.failure(type(), "webhook url is empty");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return NotifyResult.failure(type(), "webhook url is invalid");
        }
        if (!isHttpUri(uri)) {
            return NotifyResult.failure(type(), "webhook url must use http or https");
        }

        try {
            String traceId = TraceContext.ensureTraceId();
            String body = objectMapper.writeValueAsString(Map.of(
                    "title", message.getTitle(),
                    "content", message.getContent(),
                    "receivers", receivers(message),
                    "templateParams", templateParams(message),
                    "traceId", traceId
            ));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout())
                    .header("Content-Type", "application/json")
                    .header(FrameworkConstants.TRACE_ID_HEADER, traceId)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return NotifyResult.success(type());
            }
            return NotifyResult.failure(type(), "webhook status=" + response.statusCode());
        } catch (Exception e) {
            String failureMessage = failureMessage(e);
            log.warn("[Webhook通知失败] title={}, error={}", message.getTitle(), failureMessage);
            return NotifyResult.failure(type(), failureMessage);
        }
    }

    private NotifyResult validateMessage(NotifyMessage message) {
        if (message == null) {
            return NotifyResult.failure(type(), "message must not be null");
        }
        if (!NotifyTextSupport.hasText(message.getTitle())) {
            return NotifyResult.failure(type(), "title must not be blank");
        }
        if (!NotifyTextSupport.hasText(message.getContent())) {
            return NotifyResult.failure(type(), "content must not be blank");
        }
        return null;
    }

    private List<String> receivers(NotifyMessage message) {
        return message.getReceivers() == null ? List.of() : message.getReceivers();
    }

    private Map<String, Object> templateParams(NotifyMessage message) {
        return message.getTemplateParams() == null ? Map.of() : message.getTemplateParams();
    }

    private boolean isHttpUri(URI uri) {
        String scheme = uri.getScheme();
        return uri.getHost() != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }

    private Duration timeout() {
        if (properties.getWebhook() == null) {
            throw new IllegalArgumentException("framework.notify.webhook must not be null");
        }
        Duration timeout = properties.getWebhook().getTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("framework.notify.webhook.timeout must be greater than 0");
        }
        return timeout;
    }

    private String failureMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
