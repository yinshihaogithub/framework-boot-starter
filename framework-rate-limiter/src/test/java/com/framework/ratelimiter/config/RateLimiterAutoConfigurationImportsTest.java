package com.framework.ratelimiter.config;

import com.framework.ratelimiter.aspect.RateLimitAspect;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterAutoConfigurationImportsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimiterAutoConfiguration.class))
            .withBean(RedissonClient.class, RateLimiterAutoConfigurationImportsTest::redissonClient);

    @Test
    void autoConfigurationRegistersRateLimitAspect() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(RateLimitAspect.class));
    }

    @Test
    void autoConfigurationBacksOffWithoutRedissonClient() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RateLimiterAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(RateLimitAspect.class));
    }

    private static RedissonClient redissonClient() {
        return (RedissonClient) Proxy.newProxyInstance(
                RedissonClient.class.getClassLoader(),
                new Class<?>[]{RedissonClient.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
