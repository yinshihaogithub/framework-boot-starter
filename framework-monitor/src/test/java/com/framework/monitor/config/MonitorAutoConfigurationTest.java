package com.framework.monitor.config;

import com.framework.job.service.JobHandler;
import com.framework.monitor.health.FrameworkHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void healthIndicatorExposesFrameworkDiagnostics() {
        Health health = new FrameworkHealthIndicator(new MonitorProperties()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("application", "framework-application")
                .containsEntry("framework", "ready")
                .containsKeys("frameworkVersion", "javaVersion", "os", "modules");
        @SuppressWarnings("unchecked")
        List<String> modules = (List<String>) health.getDetails().get("modules");
        assertThat(modules)
                .contains("framework-core", "framework-monitor", "framework-job", "framework-tools");
        assertThat(JobHandler.class.getName()).isEqualTo("com.framework.job.service.JobHandler");
    }

    @Test
    void healthIndicatorFallsBackWhenApplicationNameIsBlank() {
        MonitorProperties properties = new MonitorProperties();
        properties.setApplicationName(" ");

        Health health = new FrameworkHealthIndicator(properties).health();

        assertThat(health.getDetails()).containsEntry("application", "framework-application");
    }

    @Test
    void autoConfigurationRejectsUnsafeApplicationName() {
        contextRunner
                .withPropertyValues("framework.monitor.application-name=orders\nservice")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.monitor.application-name must not contain control characters"));
    }

    @Test
    void propertiesRejectUnsafeApplicationNameAtStartup() {
        MonitorProperties properties = new MonitorProperties();
        properties.setApplicationName("orders\nservice");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("framework.monitor.application-name must not contain control characters");
    }
}
