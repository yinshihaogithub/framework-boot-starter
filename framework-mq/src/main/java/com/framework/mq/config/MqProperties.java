package com.framework.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQ module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.mq")
public class MqProperties {

    private boolean enabled = true;
    private Provider provider = Provider.RABBIT;
    private boolean autoCreateTable = true;
    private int maxRetry = 3;
    private String failedMessageTableName = "framework_mq_failed_message";
    private DeadLetter deadLetter = new DeadLetter();
    private Retry retry = new Retry();

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
