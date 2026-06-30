package com.framework.notify.service;

import com.framework.notify.channel.NotifyChannel;
import com.framework.notify.config.NotifyProperties;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void returnsFailureClassNameWhenChannelThrowsExceptionWithoutMessage() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.WEBHOOK);
        DefaultNotifyService notifyService = new DefaultNotifyService(properties, List.of(new EmptyMessageThrowingChannel()));

        NotifyResult result = notifyService.send(new NotifyMessage()
                .setTitle("inventory alarm")
                .setContent("SKU-1001 is low"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getChannel()).isEqualTo(NotifyChannelType.WEBHOOK);
        assertThat(result.getMessage()).isEqualTo("IllegalStateException");
    }

    @Test
    void returnsFailureWhenChannelReturnsNull() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.SMS);
        DefaultNotifyService notifyService = new DefaultNotifyService(properties, List.of(new NullNotifyChannel()));

        NotifyResult result = notifyService.send(new NotifyMessage()
                .setTitle("inventory alarm")
                .setContent("SKU-1001 is low"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getChannel()).isEqualTo(NotifyChannelType.SMS);
        assertThat(result.getMessage()).contains("returned null");
    }

    @Test
    void constructorAcceptsNullChannelListAsEmpty() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.LOG);
        DefaultNotifyService notifyService = new DefaultNotifyService(properties, null);

        NotifyResult result = notifyService.send(new NotifyMessage()
                .setTitle("inventory alarm")
                .setContent("SKU-1001 is low"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("not registered");
    }

    @Test
    void constructorRejectsMissingDefaultChannel() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(null);

        assertThatThrownBy(() -> new DefaultNotifyService(properties, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default-channel");
    }

    @Test
    void constructorRejectsInvalidOrDuplicateChannels() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.LOG);

        assertThatThrownBy(() -> new DefaultNotifyService(properties, Arrays.asList((NotifyChannel) null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NotifyChannel must not be null");

        assertThatThrownBy(() -> new DefaultNotifyService(properties, List.of(new RecordingNotifyChannel(null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NotifyChannel type must not be null");

        assertThatThrownBy(() -> new DefaultNotifyService(properties, List.of(
                new RecordingNotifyChannel(NotifyChannelType.LOG),
                new RecordingNotifyChannel(NotifyChannelType.LOG))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate NotifyChannel type");
    }

    @Test
    void returnsFailureForNullMessageBeforeCallingChannel() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.LOG);
        RecordingNotifyChannel logChannel = new RecordingNotifyChannel(NotifyChannelType.LOG);
        DefaultNotifyService notifyService = new DefaultNotifyService(properties, List.of(logChannel));

        NotifyResult result = notifyService.send(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getChannel()).isEqualTo(NotifyChannelType.LOG);
        assertThat(result.getMessage()).contains("message must not be null");
        assertThat(logChannel.lastMessage()).isNull();
    }

    @Test
    void returnsFailureForBlankTitleOrContentBeforeCallingChannel() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.LOG);
        RecordingNotifyChannel logChannel = new RecordingNotifyChannel(NotifyChannelType.LOG);
        DefaultNotifyService notifyService = new DefaultNotifyService(properties, List.of(logChannel));

        NotifyResult blankTitle = notifyService.send(new NotifyMessage()
                .setTitle("\u3000")
                .setContent("SKU-1001 is low"));
        NotifyResult blankContent = notifyService.send(new NotifyMessage()
                .setTitle("inventory alarm")
                .setContent("\u00A0"));

        assertThat(blankTitle.isSuccess()).isFalse();
        assertThat(blankTitle.getMessage()).contains("title must not be blank");
        assertThat(blankContent.isSuccess()).isFalse();
        assertThat(blankContent.getMessage()).contains("content must not be blank");
        assertThat(logChannel.lastMessage()).isNull();
    }

    @Test
    void normalizesNullableCollectionsBeforeCallingChannel() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.LOG);
        RecordingNotifyChannel logChannel = new RecordingNotifyChannel(NotifyChannelType.LOG);
        DefaultNotifyService notifyService = new DefaultNotifyService(properties, List.of(logChannel));
        NotifyMessage message = new NotifyMessage()
                .setTitle("inventory alarm")
                .setContent("SKU-1001 is low")
                .setReceivers(null)
                .setTemplateParams(null);

        NotifyResult result = notifyService.send(message);

        assertThat(result.isSuccess()).isTrue();
        assertThat(logChannel.lastMessage().getReceivers()).isEmpty();
        assertThat(logChannel.lastMessage().getTemplateParams()).isEmpty();
    }

    @Test
    void normalizesTextFieldsAndReceiversBeforeCallingChannel() {
        NotifyProperties properties = new NotifyProperties();
        properties.setDefaultChannel(NotifyChannelType.LOG);
        RecordingNotifyChannel logChannel = new RecordingNotifyChannel(NotifyChannelType.LOG);
        DefaultNotifyService notifyService = new DefaultNotifyService(properties, List.of(logChannel));
        NotifyMessage message = new NotifyMessage()
                .setTitle("\u00A0inventory alarm\u3000")
                .setContent("\u3000SKU-1001 is low\u00A0")
                .setWebhookUrl("\u00A0https://example.com/hook\u3000")
                .setReceivers(Arrays.asList("\u3000ops@example.com\u00A0", "\u00A0", null, "admin@example.com"));

        NotifyResult result = notifyService.send(message);

        assertThat(result.isSuccess()).isTrue();
        assertThat(logChannel.lastMessage().getTitle()).isEqualTo("inventory alarm");
        assertThat(logChannel.lastMessage().getContent()).isEqualTo("SKU-1001 is low");
        assertThat(logChannel.lastMessage().getWebhookUrl()).isEqualTo("https://example.com/hook");
        assertThat(logChannel.lastMessage().getReceivers())
                .containsExactly("ops@example.com", "admin@example.com");
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

    private static class NullNotifyChannel implements NotifyChannel {

        @Override
        public NotifyChannelType type() {
            return NotifyChannelType.SMS;
        }

        @Override
        public NotifyResult send(NotifyMessage message) {
            return null;
        }
    }

    private static class EmptyMessageThrowingChannel implements NotifyChannel {

        @Override
        public NotifyChannelType type() {
            return NotifyChannelType.WEBHOOK;
        }

        @Override
        public NotifyResult send(NotifyMessage message) {
            throw new IllegalStateException();
        }
    }
}
