package com.framework.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Monitor module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.monitor")
public class MonitorProperties {

    private boolean enabled = true;
    private String applicationName = "framework-application";
}
