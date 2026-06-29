package com.framework.idempotent.aspect;

import com.framework.core.constant.FrameworkConstants;
import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import com.framework.idempotent.annotation.Idempotent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentAspectTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void usesBusinessKeySpelTemplate() {
        RecordingRedis redis = new RecordingRedis(true);
        PaymentService service = proxy(new PaymentService(), redis);

        assertThat(service.pay(new PayRequest("O-100"), "tenant-a")).isEqualTo("ok");

        assertThat(redis.setKeys).containsExactly(FrameworkConstants.IDEMPOTENT_PREFIX + "pay:O-100:tenant-a");
        assertThat(redis.expire).isEqualTo(30);
        assertThat(redis.unit).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void trimsLiteralBusinessKeyBeforeWritingRedis() {
        RecordingRedis redis = new RecordingRedis(true);
        PaymentService service = proxy(new PaymentService(), redis);

        assertThat(service.literalBusinessKey()).isEqualTo("ok");

        assertThat(redis.setKeys)
                .containsExactly(FrameworkConstants.IDEMPOTENT_PREFIX + "pay:literal");
    }

    @Test
    void trimsTokenHeaderBeforeWritingRedis() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/submit");
        request.addHeader("X-Idempotent-Token", " submit-token ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RecordingRedis redis = new RecordingRedis(true);
        PaymentService service = proxy(new PaymentService(), redis);

        assertThat(service.tokenSubmit()).isEqualTo("ok");

        assertThat(redis.setKeys)
                .containsExactly(FrameworkConstants.IDEMPOTENT_PREFIX + "token:submit-token");
    }

    @Test
    void duplicateRequestThrowsBusinessExceptionBeforeProceeding() {
        RecordingRedis redis = new RecordingRedis(false);
        PaymentService service = proxy(new PaymentService(), redis);

        assertThatThrownBy(() -> service.pay(new PayRequest("O-100"), "tenant-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("duplicate")
                .extracting("code")
                .isEqualTo(ResultCode.IDEMPOTENT_FAIL.getCode());
        assertThat(service.invocations).isZero();
    }

    @Test
    void redisAcquireFailureFailsClosedWithoutLeakingInfrastructureException() {
        PaymentService service = proxy(new PaymentService(), new ThrowingRedis(true, false));

        assertThatThrownBy(() -> service.pay(new PayRequest("O-101"), "tenant-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("幂等服务暂不可用，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.IDEMPOTENT_FAIL.getCode());
        assertThat(service.invocations).isZero();
    }

    @Test
    void redisNullAcquireResultFailsClosedBeforeProceeding() {
        PaymentService service = proxy(new PaymentService(), new NullAcquireRedis());

        assertThatThrownBy(() -> service.pay(new PayRequest("O-102"), "tenant-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("幂等校验失败，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.IDEMPOTENT_FAIL.getCode());
        assertThat(service.invocations).isZero();
    }

    @Test
    void businessExceptionReleasesIdempotentKey() {
        RecordingRedis redis = new RecordingRedis(true);
        PaymentService service = proxy(new PaymentService(), redis);

        assertThatThrownBy(() -> service.fail(new PayRequest("O-200")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("payment failed");

        assertThat(redis.deletedKeys)
                .containsExactly(FrameworkConstants.IDEMPOTENT_PREFIX + "pay:O-200");
    }

    @Test
    void redisReleaseFailureDoesNotMaskBusinessException() {
        PaymentService service = proxy(new PaymentService(), new ThrowingRedis(false, true));

        assertThatThrownBy(() -> service.fail(new PayRequest("O-201")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("payment failed");
    }

    @Test
    void tokenStrategyRequiresHeaderToken() {
        RecordingRedis redis = new RecordingRedis(true);
        PaymentService service = proxy(new PaymentService(), redis);

        assertThatThrownBy(service::tokenSubmit)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("幂等Token");
        assertThat(redis.setKeys).isEmpty();
    }

    @Test
    void rejectsInvalidExpireBeforeUsingRedis() {
        RecordingRedis redis = new RecordingRedis(true);
        PaymentService service = proxy(new PaymentService(), redis);

        assertThatThrownBy(service::invalidExpire)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expire");
        assertThat(redis.setKeys).isEmpty();
    }

    @Test
    void rejectsNullResolvedBusinessKeyBeforeUsingRedis() {
        RecordingRedis redis = new RecordingRedis(true);
        PaymentService service = proxy(new PaymentService(), redis);

        assertThatThrownBy(() -> service.nullBusinessKey(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
        assertThat(redis.setKeys).isEmpty();
        assertThat(service.invocations).isZero();
    }

    private static PaymentService proxy(PaymentService target, StringRedisTemplate redisTemplate) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new IdempotentAspect(redisTemplate));
        return factory.getProxy();
    }

    public static class PaymentService {
        private int invocations;

        @Idempotent(key = "pay:#{#request.orderNo}:#{#tenantId}", expire = 30,
                strategy = Idempotent.IdempotentStrategy.BUSINESS_KEY, message = "duplicate")
        public String pay(PayRequest request, String tenantId) {
            invocations++;
            return "ok";
        }

        @Idempotent(key = " pay:literal ", strategy = Idempotent.IdempotentStrategy.BUSINESS_KEY)
        public String literalBusinessKey() {
            invocations++;
            return "ok";
        }

        @Idempotent(key = "pay:#{#request.orderNo}",
                strategy = Idempotent.IdempotentStrategy.BUSINESS_KEY)
        public String fail(PayRequest request) {
            invocations++;
            throw new IllegalStateException("payment failed");
        }

        @Idempotent(strategy = Idempotent.IdempotentStrategy.TOKEN)
        public String tokenSubmit() {
            invocations++;
            return "ok";
        }

        @Idempotent(expire = 0)
        public String invalidExpire() {
            invocations++;
            return "ok";
        }

        @Idempotent(key = "#{#orderNo}", strategy = Idempotent.IdempotentStrategy.BUSINESS_KEY)
        public String nullBusinessKey(String orderNo) {
            invocations++;
            return "ok";
        }
    }

    public record PayRequest(String orderNo) {
    }

    private static final class RecordingRedis extends StringRedisTemplate {
        private final boolean acquired;
        private final List<String> setKeys = new ArrayList<>();
        private final List<String> deletedKeys = new ArrayList<>();
        private long expire;
        private TimeUnit unit;

        private RecordingRedis(boolean acquired) {
            this.acquired = acquired;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("setIfAbsent".equals(method.getName())) {
                            setKeys.add((String) args[0]);
                            expire = ((Number) args[2]).longValue();
                            unit = (TimeUnit) args[3];
                            return acquired;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        @Override
        public Boolean delete(String key) {
            deletedKeys.add(key);
            return true;
        }
    }

    private static final class NullAcquireRedis extends StringRedisTemplate {

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
        }
    }

    private static final class ThrowingRedis extends StringRedisTemplate {
        private final boolean throwOnAcquire;
        private final boolean throwOnDelete;

        private ThrowingRedis(boolean throwOnAcquire, boolean throwOnDelete) {
            this.throwOnAcquire = throwOnAcquire;
            this.throwOnDelete = throwOnDelete;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("setIfAbsent".equals(method.getName())) {
                            if (throwOnAcquire) {
                                throw unavailable();
                            }
                            return true;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        @Override
        public Boolean delete(String key) {
            if (throwOnDelete) {
                throw unavailable();
            }
            return true;
        }

        private static IllegalStateException unavailable() {
            return new IllegalStateException("redis unavailable");
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
