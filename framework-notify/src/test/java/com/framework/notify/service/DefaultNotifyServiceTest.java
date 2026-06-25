package com.framework.notify.service;

import com.framework.notify.channel.NotifyChannel;
import com.framework.notify.config.NotifyProperties;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultNotifyServiceTest {

    @Test
    void usesDefaultChannelWhenMessageDoesNotSpecifyOne() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.LOG);
        RecordingNotifyChannel logChannel = new RecordingNotifyChannel(NotifyChannelType.LOG);
        DefaultNotifyService notifyService = new DefaultNotifyService(properties, List.of(logChannel));

        NotifyMessage message = new NotifyMessage()
                .setTitle("inventory alarm")
                .setContent("SKU-1001 is low");

        NotifyResult result = notifyService.send(message);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChannel()).isEqualTo(NotifyChannelType.LOG);
        assertThat(logChannel.lastMessage()).isSameAs(message);
    }

    @Test
    void returnsFailureWhenSelectedChannelIsMissing() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.WEBHOOK);
        DefaultNotifyService notifyService = new DefaultNotifyService(properties, List.of());

        NotifyResult result = notifyService.send(new NotifyMessage()
                .setTitle("inventory alarm")
                .setContent("SKU-1001 is low"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getChannel()).isEqualTo(NotifyChannelType.WEBHOOK);
        assertThat(result.getMessage()).contains("not registered");
    }

    @Test
    void returnsFailureWhenChannelThrowsException() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.EMAIL);
        DefaultNotifyService notifyService = new DefaultNotifyService(properties, List.of(new ThrowingNotifyChannel()));

        NotifyResult result = notifyService.send(new NotifyMessage()
                .setTitle("inventory alarm")
                .setContent("SKU-1001 is low"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getChannel()).isEqualTo(NotifyChannelType.EMAIL);
        assertThat(result.getMessage()).contains("smtp unavailable");
    }

    private static class RecordingNotifyChannel implements NotifyChannel {

        private final NotifyChannelType type;
        private NotifyMessage lastMessage;

        private RecordingNotifyChannel(NotifyChannelType type) {
            this.type = type;
        }

        @Override
        public NotifyChannelType type() {
            return type;
        }

        @Override
        public NotifyResult send(NotifyMessage message) {
            lastMessage = message;
            return NotifyResult.success(type);
        }

        private NotifyMessage lastMessage() {
            return lastMessage;
        }
    }

    private static class ThrowingNotifyChannel implements NotifyChannel {

        @Override
        public NotifyChannelType type() {
            return NotifyChannelType.EMAIL;
        }

        @Override
        public NotifyResult send(NotifyMessage message) {
            throw new IllegalStateException("smtp unavailable");
        }
    }
}
