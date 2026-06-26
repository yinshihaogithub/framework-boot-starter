package com.framework.job.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XXL-JOB executor configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.job")
public class JobProperties implements InitializingBean {

    private boolean enabled = false;
    private String adminAddresses;
    private String appName = "framework-job-executor";
    private String address;
    private String ip;
    private int port = 9999;
    private String accessToken;
    private String logPath = "logs/xxl-job/jobhandler";
    private int logRetentionDays = 30;

    @Override
    public void afterPropertiesSet() {
        if (enabled) {
            validateExecutor();
        }
    }

    public void validateExecutor() {
        if (!hasText(adminAddresses)) {
            throw new IllegalArgumentException("framework.job.admin-addresses must not be blank");
        }
        if (!hasText(appName)) {
            throw new IllegalArgumentException("framework.job.app-name must not be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("framework.job.port must be between 1 and 65535");
        }
        if (!hasText(logPath)) {
            throw new IllegalArgumentException("framework.job.log-path must not be blank");
        }
        if (logRetentionDays < -1) {
            throw new IllegalArgumentException("framework.job.log-retention-days must be greater than or equal to -1");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
