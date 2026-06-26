package com.framework.retry.aspect;

import com.framework.core.exception.BusinessException;
import com.framework.retry.annotation.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerAspectTest {

    @Test
    void interpretsAnnotationFailureRateAsRatio() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        PaymentService target = new PaymentService();
        PaymentService service = proxy(target, registry);

        assertThat(service.success()).isEqualTo("ok");

        io.github.resilience4j.circuitbreaker.CircuitBreaker breaker = registry.circuitBreaker("payment");
        assertThat(breaker.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(25.0f);
    }

    @Test
    void supportsAnnotationSlidingWindowSmallerThanDefaultMinimumCalls() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        SmallWindowService service = proxy(new SmallWindowService(), registry);

        assertThat(service.success()).isEqualTo("ok");

        io.github.resilience4j.circuitbreaker.CircuitBreaker breaker = registry.circuitBreaker("small-window");
        assertThat(breaker.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
    }

    @Test
    void noFallbackUsesOriginalCheckedExceptionMessage() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        CheckedFailureService service = proxy(new CheckedFailureService(), registry);

        assertThatThrownBy(service::fail)
                .isInstanceOf(BusinessException.class)
                .hasMessage("服务暂时不可用: remote unavailable");
    }

    @Test
    void rejectsInvalidAnnotationParametersWithClearMessages() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

        assertThatThrownBy(() -> proxy(new BlankNameService(), registry).call())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@CircuitBreaker name must not be blank");

        assertThatThrownBy(() -> proxy(new InvalidFailureRateService(), registry).call())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@CircuitBreaker failureRate must be greater than 0 and less than or equal to 1");

        assertThatThrownBy(() -> proxy(new InvalidTimeoutService(), registry).call())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@CircuitBreaker timeout must be greater than 0");

        assertThatThrownBy(() -> proxy(new InvalidWindowService(), registry).call())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@CircuitBreaker slidingWindowSize must be greater than 0");

        assertThatThrownBy(() -> proxy(new InvalidOpenWaitService(), registry).call())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@CircuitBreaker waitDurationInOpenState must be greater than 0");
    }

    private static <T> T proxy(T target, CircuitBreakerRegistry registry) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new CircuitBreakerAspect(registry));
        return factory.getProxy();
    }

    public static class PaymentService {
        @CircuitBreaker(name = "payment", failureRate = 0.25, slidingWindowSize = 10, timeout = 500)
        public String success() {
            return "ok";
        }
    }

    public static class SmallWindowService {
        @CircuitBreaker(name = "small-window", slidingWindowSize = 5)
        public String success() {
            return "ok";
        }
    }

    public static class CheckedFailureService {
        @CircuitBreaker(name = "checked-failure")
        public String fail() throws IOException {
            throw new IOException("remote unavailable");
        }
    }

    public static class BlankNameService {
        @CircuitBreaker(name = " ")
        public String call() {
            return "ok";
        }
    }

    public static class InvalidFailureRateService {
        @CircuitBreaker(name = "invalid-failure-rate", failureRate = 1.5)
        public String call() {
            return "ok";
        }
    }

    public static class InvalidTimeoutService {
        @CircuitBreaker(name = "invalid-timeout", timeout = 0)
        public String call() {
            return "ok";
        }
    }

    public static class InvalidWindowService {
        @CircuitBreaker(name = "invalid-window", slidingWindowSize = 0)
        public String call() {
            return "ok";
        }
    }

    public static class InvalidOpenWaitService {
        @CircuitBreaker(name = "invalid-open-wait", waitDurationInOpenState = 0)
        public String call() {
            return "ok";
        }
    }
}
