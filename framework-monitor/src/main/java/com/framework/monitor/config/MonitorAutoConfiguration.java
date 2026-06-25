package com.framework.monitor.config;

import com.framework.monitor.health.FrameworkHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Monitor module auto configuration.
 */
@Configuration
@ConditionalOnClass(HealthIndicator.class)
@EnableConfigurationProperties(MonitorProperties.class)
@ConditionalOnProperty(prefix = "framework.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "frameworkHealthIndicator")
    public FrameworkHealthIndicator frameworkHealthIndicator(MonitorProperties properties) {
        return new FrameworkHealthIndicator(properties);
    }
}
