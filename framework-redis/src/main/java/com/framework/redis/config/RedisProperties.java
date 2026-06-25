package com.framework.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.redis")
public class RedisProperties {

    private boolean enabled = true;
    private String keyPrefix = "framework";
    private Duration defaultTtl = Duration.ofHours(1);
    private Duration lockTtl = Duration.ofSeconds(30);
}
