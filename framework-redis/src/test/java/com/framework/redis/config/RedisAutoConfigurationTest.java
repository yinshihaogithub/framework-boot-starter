package com.framework.redis.config;

import com.framework.redis.service.RedisKeyBuilder;
import com.framework.redis.service.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void autoConfigurationRegistersRedisServiceWithTemplate() {
        contextRunner
                .withBean(StringRedisTemplate.class, RedisAutoConfigurationTest::stringRedisTemplate)
                .run(context -> assertThat(context)
                        .hasSingleBean(RedisKeyBuilder.class)
                        .hasSingleBean(RedisService.class));
    }

    @Test
    void autoConfigurationNormalizesKeyPrefixAtStartup() {
        contextRunner
                .withPropertyValues("framework.redis.key-prefix= tenant-a ")
                .run(context -> assertThat(context.getBean(RedisProperties.class).getKeyPrefix())
                        .isEqualTo("tenant-a"));
    }

    @Test
    void propertiesNormalizeKeyPrefixAtStartup() {
        RedisProperties properties = new RedisProperties();
        properties.setKeyPrefix(" tenant-a ");

        properties.afterPropertiesSet();

        assertThat(properties.getKeyPrefix()).isEqualTo("tenant-a");
    }

    @Test
    void propertiesRejectControlCharactersInKeyPrefix() {
        RedisProperties properties = new RedisProperties();
        properties.setKeyPrefix("tenant\nadmin");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.redis.key-prefix");
    }

    @Test
    void autoConfigurationRejectsInvalidRedisPropertiesAtStartup() {
        contextRunner
                .withPropertyValues("framework.redis.key-prefix= ")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.redis.key-prefix"));

        contextRunner
                .withPropertyValues("framework.redis.key-prefix=tenant:")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.redis.key-prefix"));

        contextRunner
                .withPropertyValues("framework.redis.default-ttl=0s")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.redis.default-ttl"));

        contextRunner
                .withPropertyValues("framework.redis.lock-ttl=0s")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.redis.lock-ttl"));
    }

    private static StringRedisTemplate stringRedisTemplate() {
        return new StringRedisTemplate(redisConnectionFactory());
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
