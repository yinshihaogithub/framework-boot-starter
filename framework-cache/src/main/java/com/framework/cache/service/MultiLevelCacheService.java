package com.framework.cache.service;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存（L1 本地缓存 + L2 Redis 缓存）
 * L1 命中直接返回，L1 未命中查 L2，L2 未命中查 DB
 * DB 更新时先删 L2 再删 L1（延迟双删）
 */
@Slf4j
public class MultiLevelCacheService implements CacheService {

    private final LocalCacheService localCache;
    private final RedisCacheService redisCache;

    public MultiLevelCacheService(LocalCacheService localCache, RedisCacheService redisCache) {
        this.localCache = localCache;
        this.redisCache = redisCache;
    }

    @Override
    public void set(String key, Object value) {
        set(key, value, 3600, TimeUnit.SECONDS);
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        redisCache.set(key, value, ttl, unit);
        localCache.set(key, value);
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
        return get(key, type, loader, 3600, TimeUnit.SECONDS);
    }

    @Override
    public <T> T get(String key, Class<T> type, Supplier<T> loader, long ttl, TimeUnit unit) {
        // L1
        T localValue = localCache.get(key, type);
        if (localValue != null) {
            return localValue;
        }

        // L2（含防穿透/击穿）
        T redisValue = redisCache.get(key, type, loader, ttl, unit);
        if (redisValue != null) {
            localCache.set(key, redisValue);
        }
        return redisValue;
    }

    @Override
    public void delete(String key) {
        // 延迟双删：先删 Redis → 删本地 → 延迟再删 Redis
        redisCache.delete(key);
        localCache.delete(key);

        // 延迟 500ms 再删一次 Redis（防止并发场景下脏数据回填）
        new Thread(() -> {
            try {
                Thread.sleep(500);
                redisCache.delete(key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
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
}
