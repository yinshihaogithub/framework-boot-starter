package com.framework.security.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PermissionCacheServiceTest {

    private final PermissionCacheService service = new PermissionCacheService(new ThrowingRedisTemplate());

    @Test
    void readFailuresFallbackToMissingCache() {
        assertThat(service.getRoles(1L)).isNull();
        assertThat(service.getPermissions(1L)).isNull();
        assertThat(service.hasRole(1L, "ADMIN")).isFalse();
        assertThat(service.hasPermission(1L, "system:user:view")).isFalse();
    }

    @Test
    void writeFailuresDoNotLeakToBusinessFlow() {
        assertThatCode(() -> service.cacheRoles(1L, new String[]{"ADMIN"})).doesNotThrowAnyException();
        assertThatCode(() -> service.cachePermissions(1L, new String[]{"system:user:view"})).doesNotThrowAnyException();
    }

    @Test
    void deleteFailuresDoNotLeakToBusinessFlow() {
        assertThatCode(() -> service.refresh(1L)).doesNotThrowAnyException();
        assertThatCode(() -> service.refreshBatch(java.util.List.of(1L, 2L))).doesNotThrowAnyException();
        assertThatCode(service::clearAll).doesNotThrowAnyException();
    }

    private static class ThrowingRedisTemplate extends StringRedisTemplate {

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        throw new IllegalStateException("redis unavailable");
                    });
        }

        @Override
        public Boolean delete(String key) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public Long delete(Collection<String> keys) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public Set<String> keys(String pattern) {
            throw new IllegalStateException("redis unavailable");
        }
    }
}
