package com.framework.cache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Redis 缓存实现
 * 防穿透：空值缓存
 * 防击穿：互斥锁（singleflight）
 * 防雪崩：TTL 随机化
 */
@Slf4j
public class RedisCacheService implements CacheService {

    private static final String NULL_VALUE = "\0NULL\0";
    private static final String NULL_VALUE_PREFIX = "framework:cache:null:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock = new ReentrantLock();

    public RedisCacheService(StringRedisTemplate redis) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void set(String key, Object value) {
        set(key, value, 3600, TimeUnit.SECONDS);
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        try {
            String json = objectMapper.writeValueAsString(value);
            // 防雪崩：TTL 随机化（在原 TTL 基础上随机增加 0-60 秒）
            long randomTtl = ttl + (long) (Math.random() * 60);
            redis.opsForValue().set(key, json, randomTtl, unit);
        } catch (Exception e) {
            log.error("[Redis缓存] 设置失败 key={}", key, e);
        }
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("[Redis缓存] 反序列化失败 key={}", key, e);
            return null;
        }
    }

    @Override
    public <T> T get(String key, Class<T> type, java.util.function.Supplier<T> loader) {
        return get(key, type, loader, 3600, TimeUnit.SECONDS);
    }

    @Override
    public <T> T get(String key, Class<T> type, java.util.function.Supplier<T> loader,
                     long ttl, TimeUnit unit) {
        // 1. 查缓存
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }

        // 2. 查空值缓存（防穿透）
        if (Boolean.TRUE.equals(redis.hasKey(NULL_VALUE_PREFIX + key))) {
            return null;
        }

        // 3. 互斥锁防击穿
        lock.lock();
        try {
            // double check
            cached = get(key, type);
            if (cached != null) {
                return cached;
            }

            // 查 DB
            T value = loader.get();

            if (value != null) {
                set(key, value, ttl, unit);
            } else {
                // 空值缓存（防穿透），短 TTL
                redis.opsForValue().set(NULL_VALUE_PREFIX + key, NULL_VALUE,
                        60, TimeUnit.SECONDS);
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
        redis.delete(NULL_VALUE_PREFIX + key);
    }

    @Override
    public void deleteByPattern(String pattern) {
        var keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    @Override
    public void expire(String key, long ttl, TimeUnit unit) {
        redis.expire(key, ttl, unit);
    }

    @Override
    public long getTtl(String key) {
        return redis.getExpire(key, TimeUnit.SECONDS);
    }

    @Override
    public void clear() {
        // 仅清除 framework: 前缀的 key
        var keys = redis.keys("framework:cache:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
