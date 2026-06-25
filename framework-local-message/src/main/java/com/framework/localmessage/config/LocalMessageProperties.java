package com.framework.localmessage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Local message module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.local-message")
public class LocalMessageProperties {

    private boolean enabled = true;
    private String tableName = "framework_local_message";
    private boolean autoCreateTable = true;
    private int maxRetry = 3;
    private int batchSize = 100;
    private Duration retryInterval = Duration.ofMinutes(1);
    private Scheduler scheduler = new Scheduler();

    @Data
    public static class Scheduler {
        private boolean enabled = true;
        private long fixedDelay = 30000;
    }
}
