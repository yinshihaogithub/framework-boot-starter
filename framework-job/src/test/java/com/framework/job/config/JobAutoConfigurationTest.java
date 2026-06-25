package com.framework.job.config;

import com.framework.job.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class JobAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JobAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersJobInfrastructure() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(JobProperties.class)
                .hasSingleBean(JobService.class)
                .hasSingleBean(ThreadPoolTaskScheduler.class));
    }
}
