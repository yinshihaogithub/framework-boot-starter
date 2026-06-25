package com.framework.cache.config;

import com.framework.cache.service.CacheService;
import com.framework.cache.service.LocalCacheService;
import com.framework.cache.service.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

    @Test
    void autoConfigurationFallsBackToLocalCacheWithoutRedis() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(CacheProperties.class)
                .hasSingleBean(LocalCacheService.class)
                .hasSingleBean(CacheService.class)
                .doesNotHaveBean(RedisCacheService.class));
    }
}
