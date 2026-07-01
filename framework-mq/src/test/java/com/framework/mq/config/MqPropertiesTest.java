package com.framework.mq.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqPropertiesTest {

    @Test
    void normalizesBoundarySpacesInTableNameAndDeadLetterQueue() {
        MqProperties properties = new MqProperties();
        properties.setFailedMessageTableName("\u00A0framework_mq_failed_message\u3000");
        properties.getDeadLetter().setQueue("\u3000framework.dead.letter.queue\u00A0");

        properties.afterPropertiesSet();

        assertThat(properties.getFailedMessageTableName()).isEqualTo("framework_mq_failed_message");
        assertThat(properties.getDeadLetter().getQueue()).isEqualTo("framework.dead.letter.queue");
    }

    @Test
    void rejectsUnicodeBlankDeadLetterQueueWhenEnabled() {
        MqProperties properties = new MqProperties();
        properties.getDeadLetter().setQueue("\u00A0\u3000");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.mq.dead-letter.queue");
    }

    @Test
    void allowsBlankDeadLetterQueueWhenDisabledAndNormalizesItToNull() {
        MqProperties properties = new MqProperties();
        properties.getDeadLetter().setEnabled(false);
        properties.getDeadLetter().setQueue("\u00A0\u3000");

        properties.afterPropertiesSet();

        assertThat(properties.getDeadLetter().getQueue()).isNull();
    }

    @Test
    void rejectsInvalidDeadLetterRestoreLimit() {
        MqProperties properties = new MqProperties();
        properties.getDeadLetter().setRestoreLimit(0);

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.mq.dead-letter.restore-limit");
    }

    @Test
    void rejectsInvalidFailedMessageTableNameAfterTrimming() {
        MqProperties properties = new MqProperties();
        properties.setFailedMessageTableName("\u00A0framework-mq-failed-message\u3000");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.mq.failed-message-table-name");
    }

    @Test
    void rejectsUnicodeBlankFailedMessageTableName() {
        MqProperties properties = new MqProperties();
        properties.setFailedMessageTableName("\u00A0\u3000");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.mq.failed-message-table-name");
    }
}
