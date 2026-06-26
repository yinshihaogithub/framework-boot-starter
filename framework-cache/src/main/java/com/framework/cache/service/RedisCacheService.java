package com.framework.cache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
    private static final long DEFAULT_TTL_SECONDS = 3600;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long defaultTtlSeconds;
    private final ReentrantLock lock = new ReentrantLock();

    public RedisCacheService(StringRedisTemplate redis) {
        this(redis, DEFAULT_TTL_SECONDS);
    }

    public RedisCacheService(StringRedisTemplate redis, long defaultTtlSeconds) {
        if (defaultTtlSeconds <= 0) {
            throw new IllegalArgumentException("framework.cache.remote.default-ttl (defaultTtl) must be greater than 0");
        }
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void set(String key, Object value) {
        set(key, value, defaultTtlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        CacheSupport.requireKey(key);
        CacheSupport.requireTtl(ttl, unit);
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
        CacheSupport.requireKey(key);
        CacheSupport.requireType(type);
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
        CacheSupport.requireLoader(loader);
        return get(key, type, loader, defaultTtlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public <T> T get(String key, Class<T> type, java.util.function.Supplier<T> loader,
                     long ttl, TimeUnit unit) {
        CacheSupport.requireKey(key);
        CacheSupport.requireType(type);
        CacheSupport.requireLoader(loader);
        CacheSupport.requireTtl(ttl, unit);
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
        CacheSupport.requireKey(key);
        redis.delete(key);
        redis.delete(NULL_VALUE_PREFIX + key);
    }

    @Override
    public void deleteByPattern(String pattern) {
        CacheSupport.requirePattern(pattern);
        Collection<String> keys = scanKeys(CacheSupport.redisGlobPattern(pattern));
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Override
    public boolean exists(String key) {
        CacheSupport.requireKey(key);
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    @Override
    public void expire(String key, long ttl, TimeUnit unit) {
        CacheSupport.requireKey(key);
        CacheSupport.requireTtl(ttl, unit);
        redis.expire(key, ttl, unit);
    }

    @Override
    public long getTtl(String key) {
        CacheSupport.requireKey(key);
        return redis.getExpire(key, TimeUnit.SECONDS);
    }

    @Override
    public void clear() {
        // 仅清除 framework: 前缀的 key
        Collection<String> keys = scanKeys("framework:cache:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(1000)
                .build());
        if (cursor == null) {
            return keys;
        }
        try {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
            return keys;
        } finally {
            cursor.close();
        }
    }
}
