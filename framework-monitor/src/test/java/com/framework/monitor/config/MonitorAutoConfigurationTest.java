package com.framework.monitor.config;

import com.framework.monitor.health.FrameworkHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MonitorAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersHealthIndicator() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(MonitorProperties.class)
                .hasSingleBean(FrameworkHealthIndicator.class)
                .hasSingleBean(HealthIndicator.class));
    }
}
