package com.framework.log.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Log module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.log")
public class LogProperties {

    private boolean enabled = true;
    private DbStorage dbStorage = new DbStorage();
    private int apiSampleRate = 0;
    private int retentionDays = 30;

    @Data
    public static class DbStorage {
        private boolean enabled = false;
    }
}
