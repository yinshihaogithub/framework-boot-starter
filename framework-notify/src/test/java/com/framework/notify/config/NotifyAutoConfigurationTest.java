package com.framework.notify.config;

import com.framework.notify.channel.LogNotifyChannel;
import com.framework.notify.channel.WebhookNotifyChannel;
import com.framework.notify.service.NotifyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NotifyAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersNotifyInfrastructure() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(NotifyProperties.class)
                .hasSingleBean(NotifyService.class)
                .hasSingleBean(LogNotifyChannel.class)
                .hasSingleBean(WebhookNotifyChannel.class));
    }
}
