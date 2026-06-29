package com.framework.lock.aspect;

import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import com.framework.lock.annotation.DistributedLock;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistributedLockAspectTest {

    @Test
    void usesSpelTemplateKeyAndReleasesLock() {
        RecordingRedisson redisson = new RecordingRedisson(true, true);
        LockedService service = proxy(new LockedService(), redisson.client());

        assertThat(service.process(7L, "web")).isEqualTo("ok");

        assertThat(redisson.keys).containsExactly("framework:lock:order:7:web");
        assertThat(redisson.waitTime).isEqualTo(2);
        assertThat(redisson.leaseTime).isEqualTo(5);
        assertThat(redisson.unit).isEqualTo(TimeUnit.MINUTES);
        assertThat(redisson.unlocked).isTrue();
    }

    @Test
    void trimsResolvedKeyBeforeUsingRedis() {
        RecordingRedisson redisson = new RecordingRedisson(true, true);
        LockedService service = proxy(new LockedService(), redisson.client());

        assertThat(service.byKey(" custom:key ")).isEqualTo("ok");

        assertThat(redisson.keys).containsExactly("framework:lock:custom:key");
    }

    @Test
    void usesWatchdogTryLockWhenLeaseTimeIsMinusOne() {
        RecordingRedisson redisson = new RecordingRedisson(true, true);
        LockedService service = proxy(new LockedService(), redisson.client());

        service.watchdog(9L);

        assertThat(redisson.watchdogMode).isTrue();
        assertThat(redisson.waitTime).isEqualTo(3);
        assertThat(redisson.unit).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void throwsBusinessExceptionWhenLockCannotBeAcquired() {
        RecordingRedisson redisson = new RecordingRedisson(false, false);
        LockedService service = proxy(new LockedService(), redisson.client());

        assertThatThrownBy(() -> service.process(7L, "web"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("busy")
                .extracting("code")
                .isEqualTo(ResultCode.LOCK_FAIL.getCode());
        assertThat(service.invocations).isZero();
        assertThat(redisson.unlocked).isFalse();
    }

    @Test
    void invokesFallbackWhenLockCannotBeAcquired() {
        RecordingRedisson redisson = new RecordingRedisson(false, false);
        LockedService service = proxy(new LockedService(), redisson.client());

        assertThat(service.withFallback("A-1")).isEqualTo("fallback:A-1");

        assertThat(service.invocations).isZero();
    }

    @Test
    void redissonAcquireFailuresFailClosedWithoutProceeding() {
        LockedService service = proxy(new LockedService(), new ThrowingRedisson(true, false).client());

        assertThatThrownBy(() -> service.process(7L, "web"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("busy")
                .extracting("code")
                .isEqualTo(ResultCode.LOCK_FAIL.getCode());
        assertThat(service.invocations).isZero();
    }

    @Test
    void unlockFailuresDoNotMaskBusinessResult() {
        LockedService target = new LockedService();
        LockedService service = proxy(target, new ThrowingRedisson(false, true).client());

        assertThat(service.process(7L, "web")).isEqualTo("ok");
        assertThat(target.invocations).isEqualTo(1);
    }

    @Test
    void businessExceptionsAreNotConvertedToLockFailures() {
        LockedService service = proxy(new LockedService(), new RecordingRedisson(true, true).client());

        assertThatThrownBy(service::businessFailure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("business failed");
    }

    @Test
    void rejectsInvalidTimingConfigurationBeforeUsingRedis() {
        RecordingRedisson redisson = new RecordingRedisson(true, true);
        LockedService service = proxy(new LockedService(), redisson.client());

        assertThatThrownBy(service::negativeWaitTime)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("waitTime");
        assertThatThrownBy(service::invalidLeaseTime)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leaseTime");
        assertThat(redisson.keys).isEmpty();
    }

    @Test
    void rejectsNullResolvedKeyBeforeUsingRedis() {
        RecordingRedisson redisson = new RecordingRedisson(true, true);
        LockedService service = proxy(new LockedService(), redisson.client());

        assertThatThrownBy(() -> service.nullKey(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
        assertThat(redisson.keys).isEmpty();
        assertThat(service.invocations).isZero();
    }

    private static LockedService proxy(LockedService target, RedissonClient redissonClient) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new DistributedLockAspect(redissonClient));
        return factory.getProxy();
    }

    public static class LockedService {
        private int invocations;
        private int fallbackInvocations;

        @DistributedLock(key = "order:#{#orderId}:#{#deviceId}", waitTime = 2,
                leaseTime = 5, unit = TimeUnit.MINUTES, message = "busy")
        public String process(Long orderId, String deviceId) {
            invocations++;
            return "ok";
        }

        @DistributedLock(key = "#{#key}")
        public String byKey(String key) {
            invocations++;
            return "ok";
        }

        @DistributedLock(key = "order:#{#orderId}")
        public String watchdog(Long orderId) {
            invocations++;
            return "ok";
        }

        @DistributedLock(key = "batch:#{#batchNo}", waitTime = 1, fallback = "fallback")
        public String withFallback(String batchNo) {
            invocations++;
            return "ok";
        }

        @DistributedLock(key = "invalid:wait", waitTime = -1)
        public String negativeWaitTime() {
            invocations++;
            return "ok";
        }

        @DistributedLock(key = "invalid:lease", leaseTime = 0)
        public String invalidLeaseTime() {
            invocations++;
            return "ok";
        }

        @DistributedLock(key = "#{#orderId}")
        public String nullKey(Long orderId) {
            invocations++;
            return "ok";
        }

        @DistributedLock(key = "business:failure")
        public String businessFailure() {
            invocations++;
            throw new IllegalStateException("business failed");
        }

        public String fallback(String batchNo) {
            fallbackInvocations++;
            return "fallback:" + batchNo;
        }
    }

    private static final class RecordingRedisson {
        private final boolean acquire;
        private final boolean heldByCurrentThread;
        private final List<String> keys = new ArrayList<>();
        private long waitTime;
        private long leaseTime;
        private TimeUnit unit;
        private boolean watchdogMode;
        private boolean unlocked;

        private RecordingRedisson(boolean acquire, boolean heldByCurrentThread) {
            this.acquire = acquire;
            this.heldByCurrentThread = heldByCurrentThread;
        }

        private RedissonClient client() {
            return (RedissonClient) Proxy.newProxyInstance(
                    RedissonClient.class.getClassLoader(),
                    new Class<?>[]{RedissonClient.class},
                    (proxy, method, args) -> {
                        if ("getLock".equals(method.getName())) {
                            keys.add((String) args[0]);
                            return lock();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private RLock lock() {
            return (RLock) Proxy.newProxyInstance(
                    RLock.class.getClassLoader(),
                    new Class<?>[]{RLock.class},
                    (proxy, method, args) -> {
                        if ("tryLock".equals(method.getName()) && args.length == 2) {
                            watchdogMode = true;
                            waitTime = ((Number) args[0]).longValue();
                            unit = (TimeUnit) args[1];
                            return acquire;
                        }
                        if ("tryLock".equals(method.getName()) && args.length == 3) {
                            waitTime = ((Number) args[0]).longValue();
                            leaseTime = ((Number) args[1]).longValue();
                            unit = (TimeUnit) args[2];
                            return acquire;
                        }
                        if ("isHeldByCurrentThread".equals(method.getName())) {
                            return heldByCurrentThread;
                        }
                        if ("unlock".equals(method.getName())) {
                            unlocked = true;
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static final class ThrowingRedisson {
        private final boolean throwOnTryLock;
        private final boolean throwOnUnlock;

        private ThrowingRedisson(boolean throwOnTryLock, boolean throwOnUnlock) {
            this.throwOnTryLock = throwOnTryLock;
            this.throwOnUnlock = throwOnUnlock;
        }

        private RedissonClient client() {
            return (RedissonClient) Proxy.newProxyInstance(
                    RedissonClient.class.getClassLoader(),
                    new Class<?>[]{RedissonClient.class},
                    (proxy, method, args) -> {
                        if ("getLock".equals(method.getName())) {
                            return lock();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private RLock lock() {
            return (RLock) Proxy.newProxyInstance(
                    RLock.class.getClassLoader(),
                    new Class<?>[]{RLock.class},
                    (proxy, method, args) -> {
                        if ("tryLock".equals(method.getName())) {
                            if (throwOnTryLock) {
                                throw unavailable();
                            }
                            return true;
                        }
                        if ("isHeldByCurrentThread".equals(method.getName())) {
                            return true;
                        }
                        if ("unlock".equals(method.getName())) {
                            if (throwOnUnlock) {
                                throw unavailable();
                            }
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private static IllegalStateException unavailable() {
            return new IllegalStateException("redisson unavailable");
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
