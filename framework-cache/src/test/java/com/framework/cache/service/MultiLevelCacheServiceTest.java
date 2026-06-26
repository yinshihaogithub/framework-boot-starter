package com.framework.cache.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MultiLevelCacheServiceTest {

    @Test
    void deleteDoesNotCreateNonDaemonDelayThread() throws Exception {
        RecordingRedisCacheService redisCache = new RecordingRedisCacheService();
        MultiLevelCacheService cacheService = new MultiLevelCacheService(new LocalCacheService(100, 60), redisCache);
        Set<Long> existingThreadIds = currentThreadIds();

        cacheService.delete("user:1");
        Thread.sleep(50);

        List<Thread> nonDaemonDelayThreads = Thread.getAllStackTraces().entrySet().stream()
                .filter(entry -> !existingThreadIds.contains(entry.getKey().getId()))
                .filter(entry -> !entry.getKey().isDaemon())
                .filter(entry -> Arrays.stream(entry.getValue())
                        .anyMatch(frame -> MultiLevelCacheService.class.getName().equals(frame.getClassName())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        assertThat(nonDaemonDelayThreads).isEmpty();
    }

    @Test
    void deletePerformsDelayedSecondRedisDelete() throws Exception {
        RecordingRedisCacheService redisCache = new RecordingRedisCacheService();
        MultiLevelCacheService cacheService = new MultiLevelCacheService(new LocalCacheService(100, 60), redisCache);

        cacheService.delete("user:1");

        assertThat(redisCache.deletedKeys).containsExactly("user:1");
        Thread.sleep(650);
        assertThat(redisCache.deletedKeys).containsExactly("user:1", "user:1");
    }

    @Test
    void ttlSetDoesNotLeaveStaleValueInLocalCache() throws Exception {
        RecordingRedisCacheService redisCache = new RecordingRedisCacheService();
        MultiLevelCacheService cacheService = new MultiLevelCacheService(new LocalCacheService(100, 60), redisCache);

        cacheService.set("session:1", "alice", 20, TimeUnit.MILLISECONDS);

        assertThat(cacheService.get("session:1", String.class)).isEqualTo("alice");
        Thread.sleep(80);
        assertThat(cacheService.get("session:1", String.class)).isNull();
    }

    @Test
    void expireAppliesTtlToLocalCacheBeforeDelegatingToRedisTtl() throws Exception {
        RecordingRedisCacheService redisCache = new RecordingRedisCacheService();
        MultiLevelCacheService cacheService = new MultiLevelCacheService(new LocalCacheService(100, 60), redisCache);
        cacheService.set("session:2", "bob");

        cacheService.expire("session:2", 20, TimeUnit.MILLISECONDS);

        assertThat(redisCache.expiredKeys).containsExactly("session:2");
        assertThat(cacheService.get("session:2", String.class)).isEqualTo("bob");
        Thread.sleep(80);
        assertThat(cacheService.get("session:2", String.class)).isNull();
    }

    private static Set<Long> currentThreadIds() {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getId)
                .collect(Collectors.toSet());
    }

    private static final class RecordingRedisCacheService extends RedisCacheService {

        private final List<String> deletedKeys = new CopyOnWriteArrayList<>();
        private final List<String> expiredKeys = new CopyOnWriteArrayList<>();

        private RecordingRedisCacheService() {
            super(new StringRedisTemplate());
        }

        @Override
        public void set(String key, Object value) {
            CacheSupport.requireKey(key);
        }

        @Override
        public void set(String key, Object value, long ttl, TimeUnit unit) {
            CacheSupport.requireKey(key);
            CacheSupport.requireTtl(ttl, unit);
        }

        @Override
        public <T> T get(String key, Class<T> type) {
            CacheSupport.requireKey(key);
            CacheSupport.requireType(type);
            return null;
        }

        @Override
        public void delete(String key) {
            CacheSupport.requireKey(key);
            deletedKeys.add(key);
        }

        @Override
        public void expire(String key, long ttl, TimeUnit unit) {
            CacheSupport.requireKey(key);
            CacheSupport.requireTtl(ttl, unit);
            expiredKeys.add(key);
        }
    }
}
