package com.framework.ratelimiter.aspect;

import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import com.framework.ratelimiter.annotation.RateLimit;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitAspectTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void usesCustomSpelKeyWhenConfigured() {
        RecordingRedisson redisson = new RecordingRedisson(true);
        RateLimitedService service = proxy(new RateLimitedService(), redisson.client());

        assertThat(service.byUser(42L)).isEqualTo("ok");

        assertThat(redisson.keys).containsExactly("framework:rate:default:api:user:42");
        assertThat(redisson.rateType).isEqualTo(RateType.OVERALL);
        assertThat(redisson.limit).isEqualTo(5);
        assertThat(redisson.window).isEqualTo(2);
        assertThat(redisson.unit).isEqualTo(RateIntervalUnit.MINUTES);
    }

    @Test
    void trimsResolvedConfiguredKeyBeforeUsingRedis() {
        RecordingRedisson redisson = new RecordingRedisson(true);
        RateLimitedService service = proxy(new RateLimitedService(), redisson.client());

        assertThat(service.byKey(" custom:key ")).isEqualTo("ok");

        assertThat(redisson.keys).containsExactly("framework:rate:global:custom:key");
    }

    @Test
    void buildsIpKeyFromForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/limited");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RecordingRedisson redisson = new RecordingRedisson(true);
        RateLimitedService service = proxy(new RateLimitedService(), redisson.client());

        service.byIp();

        assertThat(redisson.keys).containsExactly("framework:rate:ip:203.0.113.10:RateLimitedService:byIp");
    }

    @Test
    void throwsBusinessExceptionWhenTokenCannotBeAcquired() {
        RecordingRedisson redisson = new RecordingRedisson(false);
        RateLimitedService service = proxy(new RateLimitedService(), redisson.client());

        assertThatThrownBy(service::blocked)
                .isInstanceOf(BusinessException.class)
                .hasMessage("too many requests")
                .extracting("code")
                .isEqualTo(ResultCode.RATE_LIMITED.getCode());
        assertThat(service.invocations).isZero();
    }

    @Test
    void redissonLookupFailuresFailClosedBeforeProceeding() {
        RateLimitedService service = proxy(new RateLimitedService(),
                new ThrowingRedisson(ThrowingRedisson.FailurePoint.GET_RATE_LIMITER).client());

        assertThatThrownBy(service::blocked)
                .isInstanceOf(BusinessException.class)
                .hasMessage("限流服务暂不可用，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.RATE_LIMITED.getCode());
        assertThat(service.invocations).isZero();
    }

    @Test
    void rateLimiterAcquireFailuresFailClosedBeforeProceeding() {
        RateLimitedService service = proxy(new RateLimitedService(),
                new ThrowingRedisson(ThrowingRedisson.FailurePoint.TRY_ACQUIRE).client());

        assertThatThrownBy(service::blocked)
                .isInstanceOf(BusinessException.class)
                .hasMessage("限流服务暂不可用，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.RATE_LIMITED.getCode());
        assertThat(service.invocations).isZero();
    }

    @Test
    void rejectsInvalidLimitConfigurationBeforeUsingRedis() {
        RecordingRedisson redisson = new RecordingRedisson(true);
        RateLimitedService service = proxy(new RateLimitedService(), redisson.client());

        assertThatThrownBy(service::invalid)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThat(redisson.keys).isEmpty();
    }

    @Test
    void rejectsUnsupportedTimeUnitBeforeUsingRedis() {
        RecordingRedisson redisson = new RecordingRedisson(true);
        RateLimitedService service = proxy(new RateLimitedService(), redisson.client());

        assertThatThrownBy(service::unsupportedUnit)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit");
        assertThat(redisson.keys).isEmpty();
    }

    @Test
    void rejectsNullResolvedConfiguredKeyBeforeUsingRedis() {
        RecordingRedisson redisson = new RecordingRedisson(true);
        RateLimitedService service = proxy(new RateLimitedService(), redisson.client());

        assertThatThrownBy(() -> service.nullKey(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
        assertThat(redisson.keys).isEmpty();
        assertThat(service.invocations).isZero();
    }

    @Test
    void rejectsInvalidSpelKeyBeforeUsingRedis() {
        RecordingRedisson redisson = new RecordingRedisson(true);
        RateLimitedService service = proxy(new RateLimitedService(), redisson.client());

        assertThatThrownBy(service::invalidSpelKey)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@RateLimit key SpEL parse failed");
        assertThat(redisson.keys).isEmpty();
        assertThat(service.invocations).isZero();
    }

    private static RateLimitedService proxy(RateLimitedService target, RedissonClient redissonClient) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new RateLimitAspect(redissonClient));
        return factory.getProxy();
    }

    public static class RateLimitedService {
        private int invocations;

        @RateLimit(key = "api:user:#{#userId}", limit = 5, window = 2,
                unit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.DEFAULT)
        public String byUser(Long userId) {
            invocations++;
            return "ok";
        }

        @RateLimit(key = "#{#key}", limit = 1, window = 1)
        public String byKey(String key) {
            invocations++;
            return "ok";
        }

        @RateLimit(limit = 3, window = 1, limitType = RateLimit.LimitType.IP)
        public String byIp() {
            invocations++;
            return "ok";
        }

        @RateLimit(limit = 1, window = 1, message = "too many requests")
        public String blocked() {
            invocations++;
            return "ok";
        }

        @RateLimit(limit = 0, window = 1)
        public String invalid() {
            invocations++;
            return "ok";
        }

        @RateLimit(limit = 1, window = 100, unit = TimeUnit.MILLISECONDS)
        public String unsupportedUnit() {
            invocations++;
            return "ok";
        }

        @RateLimit(key = "#{#userId}", limit = 1, window = 1)
        public String nullKey(Long userId) {
            invocations++;
            return "ok";
        }

        @RateLimit(key = "api:#{#missing.value}", limit = 1, window = 1)
        public String invalidSpelKey() {
            invocations++;
            return "ok";
        }
    }

    private static final class RecordingRedisson {
        private final boolean acquire;
        private final List<String> keys = new ArrayList<>();
        private RateType rateType;
        private long limit;
        private long window;
        private RateIntervalUnit unit;

        private RecordingRedisson(boolean acquire) {
            this.acquire = acquire;
        }

        private RedissonClient client() {
            return (RedissonClient) Proxy.newProxyInstance(
                    RedissonClient.class.getClassLoader(),
                    new Class<?>[]{RedissonClient.class},
                    (proxy, method, args) -> {
                        if ("getRateLimiter".equals(method.getName())) {
                            keys.add((String) args[0]);
                            return rateLimiter();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private RRateLimiter rateLimiter() {
            return (RRateLimiter) Proxy.newProxyInstance(
                    RRateLimiter.class.getClassLoader(),
                    new Class<?>[]{RRateLimiter.class},
                    (proxy, method, args) -> {
                        if ("trySetRate".equals(method.getName())) {
                            rateType = (RateType) args[0];
                            limit = ((Number) args[1]).longValue();
                            window = ((Number) args[2]).longValue();
                            unit = (RateIntervalUnit) args[3];
                            return true;
                        }
                        if ("tryAcquire".equals(method.getName()) && method.getParameterCount() == 0) {
                            return acquire;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static final class ThrowingRedisson {
        private final FailurePoint failurePoint;

        private ThrowingRedisson(FailurePoint failurePoint) {
            this.failurePoint = failurePoint;
        }

        private RedissonClient client() {
            return (RedissonClient) Proxy.newProxyInstance(
                    RedissonClient.class.getClassLoader(),
                    new Class<?>[]{RedissonClient.class},
                    (proxy, method, args) -> {
                        if ("getRateLimiter".equals(method.getName())) {
                            if (failurePoint == FailurePoint.GET_RATE_LIMITER) {
                                throw new IllegalStateException("redisson unavailable");
                            }
                            return rateLimiter();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private RRateLimiter rateLimiter() {
            return (RRateLimiter) Proxy.newProxyInstance(
                    RRateLimiter.class.getClassLoader(),
                    new Class<?>[]{RRateLimiter.class},
                    (proxy, method, args) -> {
                        if ("trySetRate".equals(method.getName())) {
                            return true;
                        }
                        if ("tryAcquire".equals(method.getName()) && method.getParameterCount() == 0) {
                            throw new IllegalStateException("rate limiter unavailable");
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private enum FailurePoint {
            GET_RATE_LIMITER,
            TRY_ACQUIRE
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
