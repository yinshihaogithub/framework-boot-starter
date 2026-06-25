package com.framework.redis.service;

import com.framework.redis.config.RedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * Small StringRedisTemplate facade.
 */
public class RedisService {

    private final StringRedisTemplate redisTemplate;
    private final RedisProperties properties;

    public RedisService(StringRedisTemplate redisTemplate, RedisProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value, properties.getDefaultTtl());
    }

    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public String tryLock(String key) {
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, token, properties.getLockTtl());
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    public boolean unlock(String key, String token) {
        String current = redisTemplate.opsForValue().get(key);
        if (!token.equals(current)) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }
}
