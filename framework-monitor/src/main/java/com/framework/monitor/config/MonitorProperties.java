package com.framework.monitor.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Monitor module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.monitor")
public class MonitorProperties implements InitializingBean {

    private boolean enabled = true;
    private String applicationName = "framework-application";

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    public void validate() {
        if (applicationName == null || applicationName.isBlank()) {
            return;
        }
        for (int i = 0; i < applicationName.length(); i++) {
            if (Character.isISOControl(applicationName.charAt(i))) {
                throw new IllegalArgumentException("framework.monitor.application-name must not contain control characters");
            }
        }
    }
}
