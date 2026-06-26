package com.framework.mq.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageWrapperTest {

    @Test
    void rejectsNullPayload() {
        assertThatThrownBy(() -> MessageWrapper.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void rejectsBlankMessageType() {
        assertThatThrownBy(() -> MessageWrapper.of("ORDER-1", " ", "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message type");
    }
}
