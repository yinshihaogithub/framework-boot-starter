package com.framework.redis.service;

import com.framework.redis.config.RedisProperties;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builds normalized Redis keys.
 */
public class RedisKeyBuilder {

    private final RedisProperties properties;

    public RedisKeyBuilder(RedisProperties properties) {
        this.properties = properties;
    }

    public String build(String namespace, Object... parts) {
        String suffix = Arrays.stream(parts)
                .map(String::valueOf)
                .collect(Collectors.joining(":"));
        if (suffix.isEmpty()) {
            return properties.getKeyPrefix() + ":" + namespace;
        }
        return properties.getKeyPrefix() + ":" + namespace + ":" + suffix;
    }
}
