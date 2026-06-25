package com.framework.redis.config;

import com.framework.redis.service.RedisKeyBuilder;
import com.framework.redis.service.RedisService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis module auto configuration.
 */
@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(RedisProperties.class)
@ConditionalOnProperty(prefix = "framework.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisKeyBuilder redisKeyBuilder(RedisProperties properties) {
        return new RedisKeyBuilder(properties);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public RedisService redisService(StringRedisTemplate redisTemplate, RedisProperties properties) {
        return new RedisService(redisTemplate, properties);
    }
}
