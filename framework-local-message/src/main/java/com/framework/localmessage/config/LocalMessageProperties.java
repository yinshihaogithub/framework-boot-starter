package com.framework.localmessage.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Local message module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.local-message")
public class LocalMessageProperties implements InitializingBean {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private boolean enabled = true;
    private String tableName = "framework_local_message";
    private boolean autoCreateTable = true;
    private int maxRetry = 3;
    private int batchSize = 100;
    private Duration retryInterval = Duration.ofMinutes(1);
    private Scheduler scheduler = new Scheduler();

    @Override
    public void afterPropertiesSet() {
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("framework.local-message.table-name must match [A-Za-z0-9_]+");
        }
        if (maxRetry <= 0) {
            throw new IllegalArgumentException("framework.local-message.max-retry must be greater than 0");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("framework.local-message.batch-size must be greater than 0");
        }
        if (retryInterval == null || retryInterval.isZero() || retryInterval.isNegative()) {
            throw new IllegalArgumentException("framework.local-message.retry-interval must be greater than 0");
        }
        if (scheduler == null) {
            throw new IllegalArgumentException("framework.local-message.scheduler must not be null");
        }
        if (scheduler.getFixedDelay() <= 0) {
            throw new IllegalArgumentException("framework.local-message.scheduler.fixed-delay must be greater than 0");
        }
    }

    @Data
    public static class Scheduler {
        private boolean enabled = true;
        private long fixedDelay = 30000;
    }
}
