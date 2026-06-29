package com.framework.notify.channel;

import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogNotifyChannelTest {

    @Test
    void returnsFailureForNullMessageBeforeLogging() {
        LogNotifyChannel channel = new LogNotifyChannel();

        NotifyResult result = channel.send(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getChannel()).isEqualTo(NotifyChannelType.LOG);
        assertThat(result.getMessage()).contains("message must not be null");
    }

    @Test
    void returnsFailureForBlankTitleOrContentBeforeLogging() {
        LogNotifyChannel channel = new LogNotifyChannel();

        NotifyResult blankTitle = channel.send(message().setTitle(" "));
        NotifyResult blankContent = channel.send(message().setContent(" "));

        assertThat(blankTitle.isSuccess()).isFalse();
        assertThat(blankTitle.getMessage()).contains("title must not be blank");
        assertThat(blankContent.isSuccess()).isFalse();
        assertThat(blankContent.getMessage()).contains("content must not be blank");
    }

    @Test
    void logsValidMessageSuccessfully() {
        LogNotifyChannel channel = new LogNotifyChannel();

        NotifyResult result = channel.send(message());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChannel()).isEqualTo(NotifyChannelType.LOG);
    }

    private NotifyMessage message() {
        return NotifyMessage.of(NotifyChannelType.LOG, "inventory alarm", "SKU-1001 is low");
    }
}
