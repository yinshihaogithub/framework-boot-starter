package com.framework.log.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Log module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.log")
public class LogProperties implements InitializingBean {

    private boolean enabled = true;
    private DbStorage dbStorage = new DbStorage();
    private int apiSampleRate = 0;
    private int retentionDays = 30;

    @Override
    public void afterPropertiesSet() {
        if (dbStorage == null) {
            throw new IllegalArgumentException("framework.log.db-storage must not be null");
        }
        if (apiSampleRate < 0 || apiSampleRate > 100) {
            throw new IllegalArgumentException("framework.log.api-sample-rate must be between 0 and 100");
        }
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("framework.log.retention-days must be greater than 0");
        }
    }

    @Data
    public static class DbStorage {
        private boolean enabled = false;
    }
}
