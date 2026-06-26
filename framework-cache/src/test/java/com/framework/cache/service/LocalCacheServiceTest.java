package com.framework.cache.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCacheServiceTest {

    @Test
    void deleteByPatternTreatsRegexCharactersAsLiteralText() {
        LocalCacheService cacheService = new LocalCacheService(100, 60);
        cacheService.set("user[prod]:1", "alice");
        cacheService.set("user[prod]:2", "bob");
        cacheService.set("userp:1", "mallory");

        cacheService.deleteByPattern("user[prod]:*");

        assertThat(cacheService.exists("user[prod]:1")).isFalse();
        assertThat(cacheService.exists("user[prod]:2")).isFalse();
        assertThat(cacheService.exists("userp:1")).isTrue();
    }

    @Test
    void rejectsInvalidLocalCacheConfiguration() {
        assertThatThrownBy(() -> new LocalCacheService(0, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSize");
        assertThatThrownBy(() -> new LocalCacheService(100, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expireAfterWriteSeconds");
    }

    @Test
    void rejectsBlankKeyBeforeWritingLocalCache() {
        LocalCacheService cacheService = new LocalCacheService(100, 60);

        assertThatThrownBy(() -> cacheService.set("", "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key must not be blank");
    }

    @Test
    void setWithTtlExpiresEntryBeforeDefaultLocalTtl() throws Exception {
        LocalCacheService cacheService = new LocalCacheService(100, 60);

        cacheService.set("session:1", "alice", 20, TimeUnit.MILLISECONDS);

        assertThat(cacheService.get("session:1", String.class)).isEqualTo("alice");
        Thread.sleep(80);
        assertThat(cacheService.get("session:1", String.class)).isNull();
        assertThat(cacheService.exists("session:1")).isFalse();
    }
}
