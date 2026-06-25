package com.framework.redis.config;

import com.framework.redis.service.RedisKeyBuilder;
import com.framework.redis.service.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersKeyBuilderAndSkipsRedisServiceWithoutTemplate() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(RedisProperties.class)
                .hasSingleBean(RedisKeyBuilder.class)
                .doesNotHaveBean(RedisService.class));
    }
}
