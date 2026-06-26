package com.framework.cache.service;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存（L1 本地缓存 + L2 Redis 缓存）
 * L1 命中直接返回，L1 未命中查 L2，L2 未命中查 DB
 * DB 更新时先删 L2 再删 L1（延迟双删）
 */
@Slf4j
public class MultiLevelCacheService implements CacheService {

    private static final long DELAYED_DELETE_DELAY_MILLIS = 500L;

    private final LocalCacheService localCache;
    private final RedisCacheService redisCache;
    private final ScheduledExecutorService delayedDeleteExecutor;

    public MultiLevelCacheService(LocalCacheService localCache, RedisCacheService redisCache) {
        this(localCache, redisCache, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "framework-cache-delayed-delete");
            thread.setDaemon(true);
            return thread;
        }));
    }

    MultiLevelCacheService(LocalCacheService localCache,
                           RedisCacheService redisCache,
                           ScheduledExecutorService delayedDeleteExecutor) {
        this.localCache = Objects.requireNonNull(localCache, "localCache must not be null");
        this.redisCache = Objects.requireNonNull(redisCache, "redisCache must not be null");
        this.delayedDeleteExecutor = Objects.requireNonNull(delayedDeleteExecutor,
                "delayedDeleteExecutor must not be null");
    }

    @Override
    public void set(String key, Object value) {
        redisCache.set(key, value);
        localCache.set(key, value);
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        redisCache.set(key, value, ttl, unit);
        localCache.set(key, value, ttl, unit);
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        // L1 本地缓存
        T localValue = localCache.get(key, type);
        if (localValue != null) {
            return localValue;
        }

        // L2 Redis 缓存
        T redisValue = redisCache.get(key, type);
        if (redisValue != null) {
            // 回填 L1
            localCache.set(key, redisValue);
        }
        return redisValue;
    }

    @Override
    public <T> T get(String key, Class<T> type, Supplier<T> loader) {
        CacheSupport.requireLoader(loader);
        T localValue = localCache.get(key, type);
        if (localValue != null) {
            return localValue;
        }
        T redisValue = redisCache.get(key, type, loader);
        if (redisValue != null) {
            localCache.set(key, redisValue);
        }
        return redisValue;
    }

    @Override
    public <T> T get(String key, Class<T> type, Supplier<T> loader, long ttl, TimeUnit unit) {
        CacheSupport.requireLoader(loader);
        CacheSupport.requireTtl(ttl, unit);
        // L1
        T localValue = localCache.get(key, type);
        if (localValue != null) {
            return localValue;
        }

        // L2（含防穿透/击穿）
        T redisValue = redisCache.get(key, type, loader, ttl, unit);
        if (redisValue != null) {
            localCache.set(key, redisValue, ttl, unit);
        }
        return redisValue;
    }

    @Override
    public void delete(String key) {
        // 延迟双删：先删 Redis → 删本地 → 延迟再删 Redis
        redisCache.delete(key);
        localCache.delete(key);

        delayedDeleteExecutor.schedule(() -> {
            try {
                redisCache.delete(key);
            } catch (Exception e) {
                log.warn("[多级缓存] 延迟删除 Redis 缓存失败 key={}", key, e);
            }
        }, DELAYED_DELETE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void deleteByPattern(String pattern) {
        redisCache.deleteByPattern(pattern);
        localCache.clear();
    }

    @Override
    public boolean exists(String key) {
        return localCache.exists(key) || redisCache.exists(key);
    }

    @Override
    public void expire(String key, long ttl, TimeUnit unit) {
        redisCache.expire(key, ttl, unit);
        localCache.expire(key, ttl, unit);
    }

    @Override
    public long getTtl(String key) {
        return redisCache.getTtl(key);
    }

    @Override
    public void clear() {
        localCache.clear();
        redisCache.clear();
    }

    public void shutdown() {
        delayedDeleteExecutor.shutdown();
    }
}
