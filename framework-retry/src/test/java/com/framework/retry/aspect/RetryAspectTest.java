package com.framework.retry.aspect;

import com.framework.retry.annotation.Retry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryAspectTest {

    @Test
    void retriesRetryableExceptionUntilSuccess() throws IOException {
        RetryService target = new RetryService();
        RetryService service = proxy(target);

        assertThat(service.succeedsAfterRetries()).isEqualTo("ok");

        assertThat(target.retryableAttempts).hasValue(3);
    }

    @Test
    void noRetryForSkipsRetriesAndFallback() {
        RetryService target = new RetryService();
        RetryService service = proxy(target);

        assertThatThrownBy(service::nonRetryableWithFallback)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad request");
        assertThat(target.nonRetryableAttempts).hasValue(1);
        assertThat(target.fallbackAttempts).hasValue(0);
    }

    @Test
    void invokesFallbackAfterRetryExhaustion() {
        RetryService target = new RetryService();
        RetryService service = proxy(target);

        assertThat(service.withFallback("A-1")).isEqualTo("fallback:A-1");

        assertThat(target.fallbackSourceAttempts).hasValue(2);
        assertThat(target.fallbackAttempts).hasValue(1);
    }

    @Test
    void rejectsInvalidConfigurationBeforeProceeding() {
        RetryService target = new RetryService();
        RetryService service = proxy(target);

        assertThatThrownBy(service::invalid)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
        assertThat(target.invalidAttempts).hasValue(0);
    }

    @Test
    void rejectsInvalidTimingConfigurationBeforeProceeding() {
        RetryService target = new RetryService();
        RetryService service = proxy(target);

        assertThatThrownBy(service::invalidMaxInterval)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxInterval");
        assertThat(target.invalidMaxIntervalAttempts).hasValue(0);

        assertThatThrownBy(service::invalidMultiplier)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiplier");
        assertThat(target.invalidMultiplierAttempts).hasValue(0);
    }

    private static RetryService proxy(RetryService target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new RetryAspect());
        return factory.getProxy();
    }

    public static class RetryService {
        private final AtomicInteger retryableAttempts = new AtomicInteger();
        private final AtomicInteger nonRetryableAttempts = new AtomicInteger();
        private final AtomicInteger fallbackSourceAttempts = new AtomicInteger();
        private final AtomicInteger fallbackAttempts = new AtomicInteger();
        private final AtomicInteger invalidAttempts = new AtomicInteger();
        private final AtomicInteger invalidMaxIntervalAttempts = new AtomicInteger();
        private final AtomicInteger invalidMultiplierAttempts = new AtomicInteger();

        @Retry(maxAttempts = 2, initialInterval = 0, retryFor = IOException.class)
        public String succeedsAfterRetries() throws IOException {
            if (retryableAttempts.incrementAndGet() < 3) {
                throw new IOException("temporary");
            }
            return "ok";
        }

        @Retry(maxAttempts = 3, initialInterval = 0,
                retryFor = RuntimeException.class,
                noRetryFor = IllegalArgumentException.class,
                fallback = "fallback")
        public String nonRetryableWithFallback() {
            nonRetryableAttempts.incrementAndGet();
            throw new IllegalArgumentException("bad request");
        }

        @Retry(maxAttempts = 1, initialInterval = 0, fallback = "fallback")
        public String withFallback(String businessKey) {
            fallbackSourceAttempts.incrementAndGet();
            throw new IllegalStateException("downstream unavailable");
        }

        public String fallback(String businessKey) {
            fallbackAttempts.incrementAndGet();
            return "fallback:" + businessKey;
        }

        @Retry(maxAttempts = -1)
        public String invalid() {
            invalidAttempts.incrementAndGet();
            return "invalid";
        }

        @Retry(maxAttempts = 1, initialInterval = 100, maxInterval = 0)
        public String invalidMaxInterval() {
            invalidMaxIntervalAttempts.incrementAndGet();
            return "invalid";
        }

        @Retry(strategy = Retry.RetryStrategy.EXPONENTIAL, multiplier = Double.POSITIVE_INFINITY)
        public String invalidMultiplier() {
            invalidMultiplierAttempts.incrementAndGet();
            return "invalid";
        }
    }
}
