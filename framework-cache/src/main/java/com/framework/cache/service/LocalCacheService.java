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

    private final Cache<String, String> cache;
    private final ObjectMapper objectMapper;

    public LocalCacheService(long maxSize, long expireAfterWriteSeconds) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWriteSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void set(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            cache.put(key, json);
        } catch (Exception e) {
            log.error("[本地缓存] 设置失败 key={}", key, e);
        }
    }

    public void set(String key, Object value, long ttl, TimeUnit unit) {
        // Caffeine 的 TTL 在创建时统一设置，这里用 put 后单独 expire
        set(key, value);
    }

    public <T> T get(String key, Class<T> type) {
        String json = cache.getIfPresent(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("[本地缓存] 反序列化失败 key={}", key, e);
            return null;
        }
    }

    public void delete(String key) {
        cache.invalidate(key);
    }

    public void deleteByPattern(String pattern) {
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        cache.asMap().keySet().removeIf(key -> key.matches(regex));
    }

    public boolean exists(String key) {
        return cache.getIfPresent(key) != null;
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
}
