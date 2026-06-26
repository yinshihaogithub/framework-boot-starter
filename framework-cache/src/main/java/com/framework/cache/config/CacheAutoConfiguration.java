package com.framework.cache.config;

import com.framework.cache.service.LocalCacheService;
import com.framework.cache.service.LocalOnlyCacheService;
import com.framework.cache.service.MultiLevelCacheService;
import com.framework.cache.service.RedisCacheService;
import com.framework.cache.service.CacheService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 缓存模块自动配置
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnProperty(prefix = "framework.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public RedisCacheService redisCacheService(StringRedisTemplate redisTemplate, CacheProperties properties) {
        return new RedisCacheService(redisTemplate, properties.getRemote().getDefaultTtl());
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalCacheService localCacheService(CacheProperties properties) {
        return new LocalCacheService(properties.getLocal().getMaxSize(),
                properties.getLocal().getExpireAfterWrite());
    }

    /**
     * 多级缓存（默认 CacheService 实现）
     * 可通过 framework.cache.multi-level=false 禁用，仅用 Redis
     */
    @Bean
    @ConditionalOnBean(RedisCacheService.class)
    @ConditionalOnMissingBean(CacheService.class)
    @ConditionalOnProperty(prefix = "framework.cache", name = "multi-level", havingValue = "true", matchIfMissing = true)
    public CacheService multiLevelCacheService(LocalCacheService localCache, RedisCacheService redisCache) {
        return new MultiLevelCacheService(localCache, redisCache);
    }

    /**
     * 仅 Redis 缓存（multi-level=false 时生效）
     */
    @Bean
    @ConditionalOnBean(RedisCacheService.class)
    @ConditionalOnMissingBean(CacheService.class)
    @ConditionalOnProperty(prefix = "framework.cache", name = "multi-level", havingValue = "false")
    public CacheService redisOnlyCacheService(RedisCacheService redisCache) {
        return redisCache;
    }

    @Bean
    @ConditionalOnMissingBean(CacheService.class)
    public CacheService localOnlyCacheService(LocalCacheService localCache) {
        return new LocalOnlyCacheService(localCache);
    }
}
