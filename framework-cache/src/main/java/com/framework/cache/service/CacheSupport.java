package com.framework.cache.service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

final class CacheSupport {

    private CacheSupport() {
    }

    static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("cache key must not be blank");
        }
    }

    static void requirePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("cache pattern must not be blank");
        }
    }

    static void requireType(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("cache type must not be null");
        }
    }

    static void requireLoader(Supplier<?> loader) {
        if (loader == null) {
            throw new IllegalArgumentException("cache loader must not be null");
        }
    }

    static void requireTtl(long ttl, TimeUnit unit) {
        if (ttl <= 0) {
            throw new IllegalArgumentException("cache ttl must be greater than 0");
        }
        if (unit == null) {
            throw new IllegalArgumentException("cache ttl unit must not be null");
        }
    }

    static Pattern wildcardPattern(String pattern) {
        requirePattern(pattern);
        StringBuilder regex = new StringBuilder();
        int start = 0;
        for (int i = 0; i < pattern.length(); i++) {
            if (pattern.charAt(i) == '*') {
                regex.append(Pattern.quote(pattern.substring(start, i))).append(".*");
                start = i + 1;
            }
        }
        regex.append(Pattern.quote(pattern.substring(start)));
        return Pattern.compile(regex.toString());
    }

    static String redisGlobPattern(String pattern) {
        requirePattern(pattern);
        StringBuilder glob = new StringBuilder(pattern.length());
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '*') {
                glob.append(ch);
            } else if (ch == '?' || ch == '[' || ch == ']' || ch == '\\') {
                glob.append('\\').append(ch);
            } else {
                glob.append(ch);
            }
        }
        return glob.toString();
    }
}
