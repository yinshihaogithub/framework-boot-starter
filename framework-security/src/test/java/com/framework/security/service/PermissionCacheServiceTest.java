package com.framework.security.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
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

    @Test
    void clearAllUsesScanInsteadOfBlockingKeys() {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        PermissionCacheService cacheService = new PermissionCacheService(redis);

        cacheService.clearAll();

        assertThat(redis.keyPatterns).isEmpty();
        assertThat(redis.scanPatterns).containsExactly("framework:perm:roles:*", "framework:perm:perms:*");
        assertThat(redis.deletedCollections)
                .containsExactly(
                        List.of("framework:perm:roles:1", "framework:perm:roles:2"),
                        List.of("framework:perm:perms:1"));
        assertThat(redis.closedCursors).isEqualTo(2);
    }

    private static class RecordingRedisTemplate extends StringRedisTemplate {

        private final List<String> keyPatterns = new ArrayList<>();
        private final List<String> scanPatterns = new ArrayList<>();
        private final List<Collection<String>> deletedCollections = new ArrayList<>();
        private int closedCursors;

        @Override
        public Set<String> keys(String pattern) {
            keyPatterns.add(pattern);
            return Set.of("framework:perm:roles:legacy");
        }

        @Override
        public Cursor<String> scan(ScanOptions options) {
            scanPatterns.add(options.getPattern());
            if ("framework:perm:roles:*".equals(options.getPattern())) {
                return new RecordingCursor(List.of("framework:perm:roles:1", "framework:perm:roles:2"));
            }
            return new RecordingCursor(List.of("framework:perm:perms:1"));
        }

        @Override
        public Long delete(Collection<String> keys) {
            deletedCollections.add(List.copyOf(keys));
            return (long) keys.size();
        }

        private final class RecordingCursor implements Cursor<String> {

            private final Iterator<String> iterator;
            private long position;
            private boolean closed;

            private RecordingCursor(Collection<String> keys) {
                this.iterator = keys.iterator();
            }

            @Override
            public long getCursorId() {
                return 0;
            }

            @Override
            public boolean isClosed() {
                return closed;
            }

            @Override
            public long getPosition() {
                return position;
            }

            @Override
            public void close() {
                closed = true;
                closedCursors++;
            }

            @Override
            public boolean hasNext() {
                return !closed && iterator.hasNext();
            }

            @Override
            public String next() {
                position++;
                return iterator.next();
            }
        }
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
        public Cursor<String> scan(ScanOptions options) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public Set<String> keys(String pattern) {
            throw new IllegalStateException("redis unavailable");
        }
    }
}
