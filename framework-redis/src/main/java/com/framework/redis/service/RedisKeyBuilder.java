package com.framework.redis.service;

import com.framework.redis.config.RedisProperties;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds normalized Redis keys.
 */
public class RedisKeyBuilder {

    private final RedisProperties properties;

    public RedisKeyBuilder(RedisProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public String build(String namespace, Object... parts) {
        String keyPrefix = requireText(properties.getKeyPrefix(), "keyPrefix");
        String normalizedNamespace = requireText(namespace, "namespace");
        if (parts == null) {
            throw new IllegalArgumentException("parts must not be null");
        }
        String suffix = Arrays.stream(parts)
                .map(RedisKeyBuilder::requirePart)
                .collect(Collectors.joining(":"));
        if (suffix.isEmpty()) {
            return keyPrefix + ":" + normalizedNamespace;
        }
        return keyPrefix + ":" + normalizedNamespace + ":" + suffix;
    }

    private static String requirePart(Object part) {
        if (part == null) {
            throw new IllegalArgumentException("part must not be null");
        }
        return requireText(String.valueOf(part), "part");
    }

    private static String requireText(String value, String fieldName) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
