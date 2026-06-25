package com.framework.retry.config;

import com.framework.retry.aspect.CircuitBreakerAspect;
import com.framework.retry.aspect.RetryAspect;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Retry + CircuitBreaker 自动配置
 * 从 yaml 读取熔断器配置，注册到 CircuitBreakerRegistry
 */
@Slf4j
@Configuration
@Import({RetryAspect.class, CircuitBreakerAspect.class})
@ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CircuitBreaker")
public class RetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "framework.circuit-breaker")
    public CircuitBreakerProperties circuitBreakerProperties() {
        return new CircuitBreakerProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry(CircuitBreakerProperties properties) {
        Map<String, io.github.resilience4j.circuitbreaker.CircuitBreakerConfig> configs = new LinkedHashMap<>();

        // 注册全局默认配置
        if (properties.getDefaultConfig() != null) {
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig defaultConfig = buildConfig(properties.getDefaultConfig());
            configs.put("default", defaultConfig);
        }

        // 注册各服务独立配置
        if (properties.getConfigs() != null) {
            properties.getConfigs().forEach((name, config) -> {
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig cbConfig = buildConfig(config);
                configs.put(name, cbConfig);
                log.info("[熔断配置] 已注册 name={}, failureRate={}, timeout={}ms",
                        name, cbConfig.getFailureRateThreshold(),
                        cbConfig.getSlowCallDurationThreshold().toMillis());
            });
        }

        return configs.isEmpty() ? CircuitBreakerRegistry.ofDefaults() : CircuitBreakerRegistry.of(configs);
    }

    private io.github.resilience4j.circuitbreaker.CircuitBreakerConfig buildConfig(
            com.framework.retry.circuitbreaker.CircuitBreakerConfig props) {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .failureRateThreshold((float) props.getFailureRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(props.getSlowCallDurationThreshold()))
                .slowCallRateThreshold((float) (props.getSlowCallRateThreshold() * 100))
                .slidingWindowSize(props.getSlidingWindowSize())
                .minimumNumberOfCalls(props.getMinimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofSeconds(props.getWaitDurationInOpenStateSeconds()))
                .permittedNumberOfCallsInHalfOpenState(props.getPermittedNumberOfCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(props.isAutomaticTransitionFromOpenToHalfOpenEnabled())
                .build();
    }

    /**
     * 熔断器配置属性
     */
    @lombok.Data
    public static class CircuitBreakerProperties {
        /** 默认配置 */
        private com.framework.retry.circuitbreaker.CircuitBreakerConfig defaultConfig;
        /** 各服务独立配置 */
        private Map<String, com.framework.retry.circuitbreaker.CircuitBreakerConfig> configs;
    }
}
