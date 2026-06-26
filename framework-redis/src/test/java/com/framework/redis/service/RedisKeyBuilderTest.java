package com.framework.redis.service;

import com.framework.redis.config.RedisProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void trimsPrefixNamespaceAndPartsBeforeBuildingKeys() {
        RedisProperties properties = new RedisProperties();
        properties.setKeyPrefix(" tenant-a ");
        RedisKeyBuilder keyBuilder = new RedisKeyBuilder(properties);

        assertThat(keyBuilder.build(" order ", " 1001 ", " status "))
                .isEqualTo("tenant-a:order:1001:status");
    }

    @Test
    void rejectsBlankPrefixNamespaceAndParts() {
        RedisProperties properties = new RedisProperties();
        properties.setKeyPrefix(" ");
        RedisKeyBuilder blankPrefixBuilder = new RedisKeyBuilder(properties);

        assertThatThrownBy(() -> blankPrefixBuilder.build("order", 1001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyPrefix");

        properties.setKeyPrefix("tenant-a");
        RedisKeyBuilder keyBuilder = new RedisKeyBuilder(properties);

        assertThatThrownBy(() -> keyBuilder.build(" ", 1001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace");
        assertThatThrownBy(() -> keyBuilder.build("order", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("part");
        assertThatThrownBy(() -> keyBuilder.build("order", (Object) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("part");
    }
}
