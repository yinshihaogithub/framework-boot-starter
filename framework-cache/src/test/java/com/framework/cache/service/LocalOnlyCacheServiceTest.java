package com.framework.cache.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LocalOnlyCacheServiceTest {

    @Test
    void loaderValueIsCachedAfterFirstMiss() {
        LocalOnlyCacheService cacheService = new LocalOnlyCacheService(new LocalCacheService(100, 60));
        AtomicInteger loads = new AtomicInteger();

        String first = cacheService.get("user:1", String.class, () -> {
            loads.incrementAndGet();
            return "alice";
        }, 30, TimeUnit.SECONDS);
        String second = cacheService.get("user:1", String.class, () -> {
            loads.incrementAndGet();
            return "bob";
        }, 30, TimeUnit.SECONDS);

        assertThat(first).isEqualTo("alice");
        assertThat(second).isEqualTo("alice");
        assertThat(loads).hasValue(1);
    }

    @Test
    void deleteByPatternRemovesMatchingLocalEntries() {
        LocalOnlyCacheService cacheService = new LocalOnlyCacheService(new LocalCacheService(100, 60));
        cacheService.set("user:1", "alice");
        cacheService.set("user:2", "bob");
        cacheService.set("order:1", "paid");

        cacheService.deleteByPattern("user:*");

        assertThat(cacheService.exists("user:1")).isFalse();
        assertThat(cacheService.exists("user:2")).isFalse();
        assertThat(cacheService.exists("order:1")).isTrue();
    }
}
