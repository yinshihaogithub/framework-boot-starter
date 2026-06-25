package com.framework.retry.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAutoConfigurationTest {

    @Test
    void buildsRegistryFromConfiguredCircuitBreakers() {
        RetryAutoConfiguration autoConfiguration = new RetryAutoConfiguration();
        RetryAutoConfiguration.CircuitBreakerProperties properties =
                new RetryAutoConfiguration.CircuitBreakerProperties();

        com.framework.retry.circuitbreaker.CircuitBreakerConfig config =
                new com.framework.retry.circuitbreaker.CircuitBreakerConfig();
        config.setFailureRateThreshold(25);
        config.setSlowCallDurationThreshold(1500);
        properties.setConfigs(java.util.Map.of("payment", config));

        CircuitBreakerRegistry registry = autoConfiguration.circuitBreakerRegistry(properties);

        assertThat(registry.getConfiguration("payment")).isPresent();
        assertThat(registry.getConfiguration("payment").orElseThrow().getFailureRateThreshold())
                .isEqualTo(25.0f);
    }
}
