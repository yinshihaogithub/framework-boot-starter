package com.framework.monitor.health;

import com.framework.monitor.config.MonitorProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Basic framework health indicator.
 */
public class FrameworkHealthIndicator implements HealthIndicator {

    private final MonitorProperties properties;

    public FrameworkHealthIndicator(MonitorProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("application", properties.getApplicationName())
                .withDetail("framework", "ready")
                .build();
    }
}
