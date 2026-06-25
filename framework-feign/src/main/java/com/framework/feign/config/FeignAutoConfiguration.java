package com.framework.feign.config;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign module auto configuration.
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
@EnableConfigurationProperties(FeignProperties.class)
@ConditionalOnProperty(prefix = "framework.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RequestInterceptor frameworkFeignRequestInterceptor(FeignProperties properties) {
        return new FrameworkFeignRequestInterceptor(properties);
    }
}
