package com.framework.cache.service;

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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisCacheServiceTest {

    @Test
    void rejectsBlankKeyBeforeWritingRedis() {
        RecordingRedis redis = new RecordingRedis();
        RedisCacheService cacheService = new RedisCacheService(redis);

        assertThatThrownBy(() -> cacheService.set(" ", "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key must not be blank");

        assertThat(redis.setKeys).isEmpty();
    }

    @Test
    void rejectsInvalidTtlBeforeWritingRedis() {
        RecordingRedis redis = new RecordingRedis();
        RedisCacheService cacheService = new RedisCacheService(redis);

        assertThatThrownBy(() -> cacheService.set("user:1", "alice", 0, TimeUnit.SECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl must be greater than 0");

        assertThat(redis.setKeys).isEmpty();
    }

    @Test
    void usesConfiguredDefaultTtlForSimpleSetAndLoader() {
        RecordingRedis redis = new RecordingRedis();
        RedisCacheService cacheService = new RedisCacheService(redis, 120);

        cacheService.set("user:1", "alice");
        cacheService.get("user:2", String.class, () -> "bob");

        assertThat(redis.setTtls).hasSize(2);
        assertThat(redis.setTtls)
                .allSatisfy(ttl -> assertThat(ttl).isBetween(120L, 179L));
    }

    @Test
    void rejectsInvalidDefaultTtl() {
        assertThatThrownBy(() -> new RedisCacheService(new RecordingRedis(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultTtl");
    }

    @Test
    void rejectsNullLoaderBeforeReadingRedis() {
        RedisCacheService cacheService = new RedisCacheService(new RecordingRedis());

        assertThatThrownBy(() -> cacheService.get("user:1", String.class, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loader must not be null");
    }

    @Test
    void deleteByPatternUsesScanAndEscapesRedisGlobCharactersExceptAsterisk() {
        RecordingRedis redis = new RecordingRedis();
        RedisCacheService cacheService = new RedisCacheService(redis);

        cacheService.deleteByPattern("user[prod]?:*");

        assertThat(redis.keyPatterns).isEmpty();
        assertThat(redis.scanPatterns).containsExactly("user\\[prod\\]\\?:*");
        assertThat(redis.deletedCollections).containsExactly(List.of("user[prod]?:1"));
    }

    @Test
    void clearUsesScanInsteadOfBlockingKeys() {
        RecordingRedis redis = new RecordingRedis();
        RedisCacheService cacheService = new RedisCacheService(redis);

        cacheService.clear();

        assertThat(redis.keyPatterns).isEmpty();
        assertThat(redis.scanPatterns).containsExactly("framework:cache:*");
        assertThat(redis.deletedCollections).containsExactly(List.of("framework:cache:user:1"));
    }

    private static final class RecordingRedis extends StringRedisTemplate {
        private final List<String> setKeys = new ArrayList<>();
        private final List<Long> setTtls = new ArrayList<>();
        private final List<String> keyPatterns = new ArrayList<>();
        private final List<String> scanPatterns = new ArrayList<>();
        private final List<Collection<String>> deletedCollections = new ArrayList<>();

        @Override
        public Boolean hasKey(String key) {
            return false;
        }

        @Override
        public Set<String> keys(String pattern) {
            keyPatterns.add(pattern);
            return Set.of("user[prod]?:1");
        }

        @Override
        public Cursor<String> scan(ScanOptions options) {
            scanPatterns.add(options.getPattern());
            if ("framework:cache:*".equals(options.getPattern())) {
                return new RecordingCursor(List.of("framework:cache:user:1"));
            }
            return new RecordingCursor(List.of("user[prod]?:1"));
        }

        @Override
        public Long delete(Collection<String> keys) {
            deletedCollections.add(keys);
            return (long) keys.size();
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("set".equals(method.getName())) {
                            setKeys.add((String) args[0]);
                            if (args != null && args.length >= 4 && args[2] instanceof Long ttl) {
                                setTtls.add(ttl);
                            }
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
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

    private static final class RecordingCursor implements Cursor<String> {

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
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public String next() {
            position++;
            return iterator.next();
        }
    }
}
