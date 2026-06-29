package com.framework.security.config;

import com.framework.security.aspect.PermissionAspect;
import com.framework.security.datascope.DataScopeInterceptor;
import com.framework.security.service.PermissionCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAutoConfigurationImportsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersPermissionAspect() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(SecurityAutoConfiguration.class)
                .hasSingleBean(PermissionAspect.class)
                .hasSingleBean(DataScopeInterceptor.class));
    }

    @Test
    void permissionCacheServiceIsOptionalWhenRedisIsMissing() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(PermissionCacheService.class));
    }

    @Test
    void permissionCacheServiceIsRegisteredWhenRedisExists() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> new StringRedisTemplate(redisConnectionFactory()))
                .run(context -> assertThat(context)
                        .hasSingleBean(PermissionCacheService.class));
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
