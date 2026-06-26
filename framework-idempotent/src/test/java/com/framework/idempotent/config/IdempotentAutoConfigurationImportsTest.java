package com.framework.idempotent.config;

import com.framework.idempotent.aspect.IdempotentAspect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentAutoConfigurationImportsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempotentAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> new StringRedisTemplate(redisConnectionFactory()));

    @Test
    void autoConfigurationRegistersIdempotentAspect() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(IdempotentAspect.class));
    }

    @Test
    void autoConfigurationBacksOffWithoutRedisTemplate() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempotentAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(IdempotentAspect.class));
    }

    private static RedisConnectionFactory redisConnectionFactory() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
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
