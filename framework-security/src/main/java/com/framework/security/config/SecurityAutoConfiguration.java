package com.framework.security.config;

import com.framework.auth.context.UserContextHolder;
import com.framework.security.aspect.PermissionAspect;
import com.framework.security.datascope.DataScopeInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.framework.security.service.PermissionCacheService;

/**
 * Security 模块自动配置
 * - 注册数据权限拦截器
 * - 注册权限缓存服务
 */
@Slf4j
@Configuration
@Import(PermissionAspect.class)
@ConditionalOnProperty(prefix = "framework.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAutoConfiguration {

    /**
     * 注册数据权限 MyBatis 拦截器
     */
    @Bean
    @ConditionalOnClass(name = "org.apache.ibatis.session.SqlSessionFactory")
    @ConditionalOnMissingBean
    public DataScopeInterceptor dataScopeInterceptor() {
        log.info("[Security] 数据权限拦截器已注册");
        return new DataScopeInterceptor();
    }

    /**
     * 权限缓存服务
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public PermissionCacheService permissionCacheService(StringRedisTemplate redisTemplate) {
        return new PermissionCacheService(redisTemplate);
    }
}
