package com.framework.redis.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.redis")
public class RedisProperties implements InitializingBean {

    private boolean enabled = true;
    private String keyPrefix = "framework";
    private Duration defaultTtl = Duration.ofHours(1);
    private Duration lockTtl = Duration.ofSeconds(30);

    @Override
    public void afterPropertiesSet() {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("framework.redis.key-prefix must not be blank");
        }
        if (defaultTtl == null || defaultTtl.isZero() || defaultTtl.isNegative()) {
            throw new IllegalArgumentException("framework.redis.default-ttl must be greater than 0");
        }
        if (lockTtl == null || lockTtl.isZero() || lockTtl.isNegative()) {
            throw new IllegalArgumentException("framework.redis.lock-ttl must be greater than 0");
        }
    }
}
