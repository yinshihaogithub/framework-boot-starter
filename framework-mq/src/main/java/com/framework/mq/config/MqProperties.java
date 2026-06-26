package com.framework.mq.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQ module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.mq")
public class MqProperties implements InitializingBean {

    private boolean enabled = true;
    private Provider provider = Provider.RABBIT;
    private boolean autoCreateTable = true;
    private int maxRetry = 3;
    private String failedMessageTableName = "framework_mq_failed_message";
    private DeadLetter deadLetter = new DeadLetter();
    private Retry retry = new Retry();

    @Override
    public void afterPropertiesSet() {
        if (provider == null) {
            throw new IllegalArgumentException("framework.mq.provider must not be null");
        }
        if (maxRetry <= 0) {
            throw new IllegalArgumentException("framework.mq.max-retry must be greater than 0");
        }
        if (failedMessageTableName == null || !failedMessageTableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("framework.mq.failed-message-table-name must match [A-Za-z0-9_]+");
        }
        if (deadLetter == null) {
            throw new IllegalArgumentException("framework.mq.dead-letter must not be null");
        }
        if (deadLetter.isEnabled() && !hasText(deadLetter.getQueue())) {
            throw new IllegalArgumentException("framework.mq.dead-letter.queue must not be blank");
        }
        if (retry == null) {
            throw new IllegalArgumentException("framework.mq.retry must not be null");
        }
        if (retry.getFixedDelay() <= 0) {
            throw new IllegalArgumentException("framework.mq.retry.fixed-delay must be greater than 0");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public enum Provider {
        RABBIT, KAFKA, ROCKET
    }

    @Data
    public static class DeadLetter {
        private boolean enabled = true;
        private String queue = "framework.dead.letter.queue";
    }

    @Data
    public static class Retry {
        private long fixedDelay = 30000L;
    }
}
