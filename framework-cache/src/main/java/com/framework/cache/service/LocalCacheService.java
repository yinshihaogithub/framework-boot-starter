package com.framework.cache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存实现
 * L1 缓存层，用于热点数据
 */
@Slf4j
public class LocalCacheService {

    private final Cache<String, CacheEntry> cache;
    private final ObjectMapper objectMapper;

    public LocalCacheService(long maxSize, long expireAfterWriteSeconds) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("local cache maxSize must be greater than 0");
        }
        if (expireAfterWriteSeconds <= 0) {
            throw new IllegalArgumentException("local cache expireAfterWriteSeconds must be greater than 0");
        }
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWriteSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void set(String key, Object value) {
        CacheSupport.requireKey(key);
        try {
            String json = objectMapper.writeValueAsString(value);
            cache.put(key, CacheEntry.withDefaultTtl(json));
        } catch (Exception e) {
            log.error("[本地缓存] 设置失败 key={}", key, e);
        }
    }

    public void set(String key, Object value, long ttl, TimeUnit unit) {
        CacheSupport.requireKey(key);
        CacheSupport.requireTtl(ttl, unit);
        try {
            String json = objectMapper.writeValueAsString(value);
            cache.put(key, CacheEntry.withDeadline(json, expiresAtNanos(ttl, unit)));
        } catch (Exception e) {
            log.error("[本地缓存] 设置失败 key={}", key, e);
        }
    }

    public <T> T get(String key, Class<T> type) {
        CacheSupport.requireKey(key);
        CacheSupport.requireType(type);
        CacheEntry entry = currentEntry(key);
        if (entry == null) {
            return null;
        }
        try {
            return objectMapper.readValue(entry.json(), type);
        } catch (Exception e) {
            log.error("[本地缓存] 反序列化失败 key={}", key, e);
            return null;
        }
    }

    public void delete(String key) {
        CacheSupport.requireKey(key);
        cache.invalidate(key);
    }

    public void deleteByPattern(String pattern) {
        var regex = CacheSupport.wildcardPattern(pattern);
        cache.asMap().keySet().removeIf(key -> regex.matcher(key).matches());
    }

    public boolean exists(String key) {
        CacheSupport.requireKey(key);
        return currentEntry(key) != null;
    }

    public void expire(String key, long ttl, TimeUnit unit) {
        CacheSupport.requireKey(key);
        CacheSupport.requireTtl(ttl, unit);
        CacheEntry entry = currentEntry(key);
        if (entry != null) {
            cache.put(key, CacheEntry.withDeadline(entry.json(), expiresAtNanos(ttl, unit)));
        }
    }

    public void clear() {
        cache.invalidateAll();
    }

    /**
     * 获取缓存统计信息
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats getStats() {
        return cache.stats();
    }

    /**
     * 获取缓存条目数
     */
    public long size() {
        return cache.estimatedSize();
    }

    private CacheEntry currentEntry(String key) {
        CacheEntry entry = cache.getIfPresent(key);
        if (entry == null) {
            return null;
        }
        if (entry.expired()) {
            cache.invalidate(key);
            return null;
        }
        return entry;
    }

    private long expiresAtNanos(long ttl, TimeUnit unit) {
        return System.nanoTime() + unit.toNanos(ttl);
    }

    private record CacheEntry(String json, long expiresAtNanos) {

        private static CacheEntry withDefaultTtl(String json) {
            return new CacheEntry(json, 0);
        }

        private static CacheEntry withDeadline(String json, long expiresAtNanos) {
            return new CacheEntry(json, expiresAtNanos);
        }

        private boolean expired() {
            return expiresAtNanos > 0 && System.nanoTime() - expiresAtNanos >= 0;
        }
    }
}
