package com.framework.redis.service;

import com.framework.redis.config.RedisProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisKeyBuilderTest {

    @Test
    void buildsKeysWithConfiguredPrefixNamespaceAndParts() {
        RedisProperties properties = new RedisProperties();
        properties.setKeyPrefix("tenant-a");
        RedisKeyBuilder keyBuilder = new RedisKeyBuilder(properties);

        assertThat(keyBuilder.build("order", 1001, "status"))
                .isEqualTo("tenant-a:order:1001:status");
        assertThat(keyBuilder.build("heartbeat"))
                .isEqualTo("tenant-a:heartbeat");
    }
}
