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
        if (properties == null) {
            throw new IllegalArgumentException("framework.circuit-breaker must not be null");
        }
        Map<String, io.github.resilience4j.circuitbreaker.CircuitBreakerConfig> configs = new LinkedHashMap<>();

        // 注册全局默认配置
        if (properties.getDefaultConfig() != null) {
            validateConfig("framework.circuit-breaker.default-config", properties.getDefaultConfig());
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig defaultConfig = buildConfig(properties.getDefaultConfig());
            configs.put("default", defaultConfig);
        }

        // 注册各服务独立配置
        if (properties.getConfigs() != null) {
            properties.getConfigs().forEach((name, config) -> {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("framework.circuit-breaker.configs name must not be blank");
                }
                String prefix = "framework.circuit-breaker.configs." + name.trim();
                validateConfig(prefix, config);
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig cbConfig = buildConfig(config);
                configs.put(name.trim(), cbConfig);
                log.info("[熔断配置] 已注册 name={}, failureRate={}, timeout={}ms",
                        name.trim(), cbConfig.getFailureRateThreshold(),
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

    private void validateConfig(String prefix, com.framework.retry.circuitbreaker.CircuitBreakerConfig props) {
        if (props == null) {
            throw new IllegalArgumentException(prefix + " must not be null");
        }
        if (props.getFailureRateThreshold() <= 0 || props.getFailureRateThreshold() > 100) {
            throw new IllegalArgumentException(prefix + ".failure-rate-threshold must be greater than 0 and less than or equal to 100");
        }
        if (props.getSlowCallDurationThreshold() <= 0) {
            throw new IllegalArgumentException(prefix + ".slow-call-duration-threshold must be greater than 0");
        }
        if (props.getSlowCallRateThreshold() <= 0 || props.getSlowCallRateThreshold() > 1) {
            throw new IllegalArgumentException(prefix + ".slow-call-rate-threshold must be greater than 0 and less than or equal to 1");
        }
        if (props.getSlidingWindowSize() <= 0) {
            throw new IllegalArgumentException(prefix + ".sliding-window-size must be greater than 0");
        }
        if (props.getMinimumNumberOfCalls() <= 0) {
            throw new IllegalArgumentException(prefix + ".minimum-number-of-calls must be greater than 0");
        }
        if (props.getMinimumNumberOfCalls() > props.getSlidingWindowSize()) {
            throw new IllegalArgumentException(prefix + ".minimum-number-of-calls must be less than or equal to sliding-window-size");
        }
        if (props.getWaitDurationInOpenStateSeconds() <= 0) {
            throw new IllegalArgumentException(prefix + ".wait-duration-in-open-state-seconds must be greater than 0");
        }
        if (props.getPermittedNumberOfCallsInHalfOpenState() <= 0) {
            throw new IllegalArgumentException(prefix + ".permitted-number-of-calls-in-half-open-state must be greater than 0");
        }
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
