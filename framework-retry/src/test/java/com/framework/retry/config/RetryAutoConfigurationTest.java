package com.framework.retry.config;

import com.framework.retry.aspect.CircuitBreakerAspect;
import com.framework.retry.aspect.RetryAspect;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RetryAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersRetryAndCircuitBreakerInfrastructure() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(RetryAspect.class)
                .hasSingleBean(CircuitBreakerAspect.class)
                .hasSingleBean(RetryAutoConfiguration.CircuitBreakerProperties.class)
                .hasSingleBean(CircuitBreakerRegistry.class));
    }

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

    @Test
    void circuitBreakerRegistryRejectsInvalidConfiguredCircuitBreakers() {
        RetryAutoConfiguration autoConfiguration = new RetryAutoConfiguration();

        RetryAutoConfiguration.CircuitBreakerProperties blankNameProperties =
                new RetryAutoConfiguration.CircuitBreakerProperties();
        blankNameProperties.setConfigs(Map.of(" ", config()));
        assertThatThrownBy(() -> autoConfiguration.circuitBreakerRegistry(blankNameProperties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.circuit-breaker.configs name must not be blank");

        RetryAutoConfiguration.CircuitBreakerProperties nullConfigProperties =
                new RetryAutoConfiguration.CircuitBreakerProperties();
        Map<String, com.framework.retry.circuitbreaker.CircuitBreakerConfig> configs = new LinkedHashMap<>();
        configs.put("payment", null);
        nullConfigProperties.setConfigs(configs);
        assertThatThrownBy(() -> autoConfiguration.circuitBreakerRegistry(nullConfigProperties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.circuit-breaker.configs.payment must not be null");

        RetryAutoConfiguration.CircuitBreakerProperties invalidFailureRateProperties =
                new RetryAutoConfiguration.CircuitBreakerProperties();
        com.framework.retry.circuitbreaker.CircuitBreakerConfig invalidFailureRate = config();
        invalidFailureRate.setFailureRateThreshold(101);
        invalidFailureRateProperties.setConfigs(Map.of("payment", invalidFailureRate));
        assertThatThrownBy(() -> autoConfiguration.circuitBreakerRegistry(invalidFailureRateProperties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.circuit-breaker.configs.payment.failure-rate-threshold");
    }

    private static com.framework.retry.circuitbreaker.CircuitBreakerConfig config() {
        return new com.framework.retry.circuitbreaker.CircuitBreakerConfig();
    }
}
