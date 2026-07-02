package com.framework.monitor.health;

import com.framework.core.module.FrameworkModuleRegistry;
import com.framework.monitor.config.MonitorProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.List;

/**
 * Basic framework health indicator.
 */
public class FrameworkHealthIndicator implements HealthIndicator {

    private static final String DEFAULT_APPLICATION_NAME = "framework-application";

    private final MonitorProperties properties;

    public FrameworkHealthIndicator(MonitorProperties properties) {
        this.properties = properties;
        properties.validate();
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("application", applicationName())
                .withDetail("framework", "ready")
                .withDetail("frameworkVersion", frameworkVersion())
                .withDetail("modules", availableModules())
                .withDetail("javaVersion", System.getProperty("java.version"))
                .withDetail("os", System.getProperty("os.name"))
                .build();
    }

    private String applicationName() {
        String applicationName = properties.getApplicationName();
        return applicationName == null || applicationName.isBlank()
                ? DEFAULT_APPLICATION_NAME
                : applicationName.trim();
    }

    private String frameworkVersion() {
        String version = FrameworkHealthIndicator.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "unknown" : version;
    }

    private List<String> availableModules() {
        return FrameworkModuleRegistry.availableModuleNames(FrameworkHealthIndicator.class.getClassLoader());
    }
}
