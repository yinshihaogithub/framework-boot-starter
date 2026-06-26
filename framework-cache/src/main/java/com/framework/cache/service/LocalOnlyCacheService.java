package com.framework.cache.service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * CacheService adapter backed by local Caffeine only.
 */
public class LocalOnlyCacheService implements CacheService {

    private final LocalCacheService localCache;

    public LocalOnlyCacheService(LocalCacheService localCache) {
        this.localCache = localCache;
    }

    @Override
    public void set(String key, Object value) {
        localCache.set(key, value);
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        localCache.set(key, value, ttl, unit);
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        return localCache.get(key, type);
    }

    @Override
    public <T> T get(String key, Class<T> type, Supplier<T> loader) {
        CacheSupport.requireLoader(loader);
        T cached = localCache.get(key, type);
        if (cached != null) {
            return cached;
        }
        T value = loader.get();
        if (value != null) {
            localCache.set(key, value);
        }
        return value;
    }

    @Override
    public <T> T get(String key, Class<T> type, Supplier<T> loader, long ttl, TimeUnit unit) {
        CacheSupport.requireLoader(loader);
        CacheSupport.requireTtl(ttl, unit);
        T cached = localCache.get(key, type);
        if (cached != null) {
            return cached;
        }
        T value = loader.get();
        if (value != null) {
            localCache.set(key, value, ttl, unit);
        }
        return value;
    }

    @Override
    public void delete(String key) {
        localCache.delete(key);
    }

    @Override
    public void deleteByPattern(String pattern) {
        localCache.deleteByPattern(pattern);
    }

    @Override
    public boolean exists(String key) {
        return localCache.exists(key);
    }

    @Override
    public void expire(String key, long ttl, TimeUnit unit) {
        localCache.expire(key, ttl, unit);
    }

    @Override
    public long getTtl(String key) {
        return -1;
    }

    @Override
    public void clear() {
        localCache.clear();
    }
}
