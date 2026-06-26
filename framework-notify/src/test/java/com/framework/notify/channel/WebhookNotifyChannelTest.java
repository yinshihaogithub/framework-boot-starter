package com.framework.notify.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
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
