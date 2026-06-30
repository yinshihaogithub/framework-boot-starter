package com.framework.notify.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import com.framework.notify.config.NotifyProperties;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookNotifyChannelTest {

    @Test
    void returnsFailureForNullMessageBeforeSending() {
        WebhookNotifyChannel channel = new WebhookNotifyChannel(new NotifyProperties(), new ObjectMapper());

        NotifyResult result = channel.send(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getChannel()).isEqualTo(NotifyChannelType.WEBHOOK);
        assertThat(result.getMessage()).contains("message must not be null");
    }

    @Test
    void returnsFailureForBlankTitleOrContentBeforeSending() {
        WebhookNotifyChannel channel = new WebhookNotifyChannel(new NotifyProperties(), new ObjectMapper());

        NotifyResult blankTitle = channel.send(message("https://example.com/hook").setTitle("\u3000"));
        NotifyResult blankContent = channel.send(message("https://example.com/hook").setContent("\u00A0"));

        assertThat(blankTitle.isSuccess()).isFalse();
        assertThat(blankTitle.getMessage()).contains("title must not be blank");
        assertThat(blankContent.isSuccess()).isFalse();
        assertThat(blankContent.getMessage()).contains("content must not be blank");
    }

    @Test
    void returnsFailureForUnsupportedWebhookScheme() {
        WebhookNotifyChannel channel = new WebhookNotifyChannel(new NotifyProperties(), new ObjectMapper());

        NotifyResult result = channel.send(message("file:///tmp/hook"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getChannel()).isEqualTo(NotifyChannelType.WEBHOOK);
        assertThat(result.getMessage()).contains("http or https");
    }

    @Test
    void returnsFailureForInvalidWebhookUrl() {
        WebhookNotifyChannel channel = new WebhookNotifyChannel(new NotifyProperties(), new ObjectMapper());

        NotifyResult result = channel.send(message("http://"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("invalid");
    }

    @Test
    void constructorRejectsInvalidTimeout() {
        NotifyProperties properties = new NotifyProperties();
        properties.getWebhook().setTimeout(Duration.ZERO);

        assertThatThrownBy(() -> new WebhookNotifyChannel(properties, new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void constructorRejectsConfiguredWebhookUrlWithUnsupportedScheme() {
        NotifyProperties properties = new NotifyProperties();
        properties.getWebhook().setUrl("file:///tmp/hook");

        assertThatThrownBy(() -> new WebhookNotifyChannel(properties, new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.notify.webhook.url");
    }

    @Test
    void sendsTraceIdInHeaderAndBody() throws Exception {
        AtomicReference<String> traceHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            traceHeader.set(exchange.getRequestHeaders().getFirst(FrameworkConstants.TRACE_ID_HEADER));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        TraceContext.putTraceId("trace-webhook-001");
        try {
            NotifyProperties properties = new NotifyProperties();
            properties.getWebhook().setUrl("\u3000http://127.0.0.1:" + server.getAddress().getPort() + "/hook\u00A0");
            WebhookNotifyChannel channel = new WebhookNotifyChannel(properties, new ObjectMapper());

            NotifyResult result = channel.send(message(null));

            assertThat(result.isSuccess()).isTrue();
            assertThat(traceHeader.get()).isEqualTo("trace-webhook-001");
            assertThat(requestBody.get()).contains("\"traceId\":\"trace-webhook-001\"");
        } finally {
            TraceContext.clear();
            server.stop(0);
        }
    }

    @Test
    void sendsEmptyCollectionsWhenOptionalMessageFieldsAreNull() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            NotifyProperties properties = new NotifyProperties();
            properties.getWebhook().setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
            WebhookNotifyChannel channel = new WebhookNotifyChannel(properties, new ObjectMapper());
            NotifyMessage message = NotifyMessage.of(NotifyChannelType.WEBHOOK, "inventory alarm", "SKU-1001 is low")
                    .setReceivers(null)
                    .setTemplateParams(null);

            NotifyResult result = channel.send(message);

            assertThat(result.isSuccess()).isTrue();
            assertThat(requestBody.get())
                    .contains("\"receivers\":[]")
                    .contains("\"templateParams\":{}");
        } finally {
            server.stop(0);
        }
    }

    private NotifyMessage message(String webhookUrl) {
        return NotifyMessage.of(NotifyChannelType.WEBHOOK, "inventory alarm", "SKU-1001 is low")
                .setWebhookUrl(webhookUrl);
    }
}
