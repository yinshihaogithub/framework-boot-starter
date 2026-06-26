package com.framework.cache.config;

import com.framework.cache.service.CacheService;
import com.framework.cache.service.LocalCacheService;
import com.framework.cache.service.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

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

    @Test
    void autoConfigurationRejectsInvalidRemoteDefaultTtlWhenRedisIsPresent() {
        contextRunner
                .withBean(StringRedisTemplate.class, TestStringRedisTemplate::new)
                .withPropertyValues("framework.cache.remote.default-ttl=0")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.cache.remote.default-ttl"));
    }

    @Test
    void autoConfigurationRejectsInvalidCachePropertiesAtStartup() {
        contextRunner
                .withPropertyValues("framework.cache.local.max-size=0")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.cache.local.max-size"));

        contextRunner
                .withPropertyValues("framework.cache.local.expire-after-write=0")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.cache.local.expire-after-write"));

        contextRunner
                .withPropertyValues("framework.cache.remote.default-ttl=0")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.cache.remote.default-ttl"));
    }

    private static class TestStringRedisTemplate extends StringRedisTemplate {
        @Override
        public void afterPropertiesSet() {
            // No RedisConnectionFactory is needed for auto-configuration wiring tests.
        }
    }
}
