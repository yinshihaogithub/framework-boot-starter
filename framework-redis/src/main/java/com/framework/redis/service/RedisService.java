package com.framework.redis.service;

import com.framework.redis.config.RedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Small StringRedisTemplate facade.
 */
public class RedisService {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisProperties properties;

    public RedisService(StringRedisTemplate redisTemplate, RedisProperties properties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(requireText(key, "key"), value,
                requirePositiveDuration(properties.getDefaultTtl(), "defaultTtl"));
    }

    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(requireText(key, "key"), value,
                requirePositiveDuration(ttl, "ttl"));
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(requireText(key, "key"));
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(requireText(key, "key")));
    }

    public String tryLock(String key) {
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(requireText(key, "key"), token,
                requirePositiveDuration(properties.getLockTtl(), "lockTtl"));
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    public boolean unlock(String key, String token) {
        Long result = redisTemplate.execute(UNLOCK_SCRIPT, List.of(requireText(key, "key")),
                requireText(token, "token"));
        return Long.valueOf(1L).equals(result);
    }

    private static String requireText(String value, String fieldName) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static Duration requirePositiveDuration(Duration duration, String fieldName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return duration;
    }
}
