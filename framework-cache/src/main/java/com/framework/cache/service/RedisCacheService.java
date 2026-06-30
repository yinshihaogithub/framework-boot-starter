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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

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
    private static final long TTL_JITTER_BOUND = 60;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long defaultTtlSeconds;
    private final LongSupplier ttlJitterSupplier;
    private final ReentrantLock lock = new ReentrantLock();

    public RedisCacheService(StringRedisTemplate redis) {
        this(redis, DEFAULT_TTL_SECONDS);
    }

    public RedisCacheService(StringRedisTemplate redis, long defaultTtlSeconds) {
        this(redis, defaultTtlSeconds, () -> ThreadLocalRandom.current().nextLong(TTL_JITTER_BOUND));
    }

    RedisCacheService(StringRedisTemplate redis, long defaultTtlSeconds, LongSupplier ttlJitterSupplier) {
        if (defaultTtlSeconds <= 0) {
            throw new IllegalArgumentException("framework.cache.remote.default-ttl (defaultTtl) must be greater than 0");
        }
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.ttlJitterSupplier = Objects.requireNonNull(ttlJitterSupplier, "ttlJitterSupplier must not be null");
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
            // 防雪崩：TTL 随机化（在原 TTL 基础上随机增加 0-59 个当前时间单位）
            long randomTtl = ttl + Math.floorMod(ttlJitterSupplier.getAsLong(), TTL_JITTER_BOUND);
            redis.opsForValue().set(key, json, randomTtl, unit);
        } catch (Exception e) {
            log.warn("[Redis缓存] 设置失败 key={} error={}", key, e.getMessage());
        }
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        CacheSupport.requireKey(key);
        CacheSupport.requireType(type);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("[Redis缓存] 读取失败 key={} error={}", key, e.getMessage());
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
        if (hasNullValue(key)) {
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
                cacheNullValue(key);
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String key) {
        CacheSupport.requireKey(key);
        try {
            redis.delete(key);
            redis.delete(NULL_VALUE_PREFIX + key);
        } catch (Exception e) {
            log.warn("[Redis缓存] 删除失败 key={} error={}", key, e.getMessage());
        }
    }

    @Override
    public void deleteByPattern(String pattern) {
        CacheSupport.requirePattern(pattern);
        Collection<String> keys = scanKeys(CacheSupport.redisGlobPattern(pattern));
        if (keys != null && !keys.isEmpty()) {
            try {
                redis.delete(keys);
            } catch (Exception e) {
                log.warn("[Redis缓存] 批量删除失败 pattern={} error={}", pattern, e.getMessage());
            }
        }
    }

    @Override
    public boolean exists(String key) {
        CacheSupport.requireKey(key);
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (Exception e) {
            log.warn("[Redis缓存] 判断存在失败 key={} error={}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void expire(String key, long ttl, TimeUnit unit) {
        CacheSupport.requireKey(key);
        CacheSupport.requireTtl(ttl, unit);
        try {
            redis.expire(key, ttl, unit);
        } catch (Exception e) {
            log.warn("[Redis缓存] 设置过期时间失败 key={} error={}", key, e.getMessage());
        }
    }

    @Override
    public long getTtl(String key) {
        CacheSupport.requireKey(key);
        try {
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            return ttl == null ? -1 : ttl;
        } catch (Exception e) {
            log.warn("[Redis缓存] 查询过期时间失败 key={} error={}", key, e.getMessage());
            return -1;
        }
    }

    @Override
    public void clear() {
        // 仅清除 framework: 前缀的 key
        Collection<String> keys = scanKeys("framework:cache:*");
        if (keys != null && !keys.isEmpty()) {
            try {
                redis.delete(keys);
            } catch (Exception e) {
                log.warn("[Redis缓存] 清空失败 error={}", e.getMessage());
            }
        }
    }

    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        try {
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
            } finally {
                try {
                    cursor.close();
                } catch (Exception e) {
                    log.warn("[Redis缓存] 关闭扫描游标失败 pattern={} error={}", pattern, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[Redis缓存] 扫描失败 pattern={} error={}", pattern, e.getMessage());
            return List.of();
        }
        return keys;
    }

    private boolean hasNullValue(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(NULL_VALUE_PREFIX + key));
        } catch (Exception e) {
            log.warn("[Redis缓存] 读取空值标记失败 key={} error={}", key, e.getMessage());
            return false;
        }
    }

    private void cacheNullValue(String key) {
        try {
            // 空值缓存（防穿透），短 TTL
            redis.opsForValue().set(NULL_VALUE_PREFIX + key, NULL_VALUE, 60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[Redis缓存] 写入空值标记失败 key={} error={}", key, e.getMessage());
        }
    }
}
