package com.framework.auth.service;

import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordExpireServiceTest {

    private static final String PASSWORD_UPDATE_PREFIX = "framework:password:update:";

    private final InMemoryRedisTemplate redis = new InMemoryRedisTemplate();

    @Test
    void disabledExpirationDoesNotWritePasswordChangeMarker() {
        PasswordExpireService service = new PasswordExpireService(redis, 0);

        service.checkPasswordExpired(null);
        service.recordPasswordChange(1L);

        assertThat(service.getRemainingDays(1L)).isEqualTo(-1);
        assertThat(redis.values).isEmpty();
    }

    @Test
    void enabledExpirationRejectsInvalidUserId() {
        PasswordExpireService service = new PasswordExpireService(redis, 30);

        assertThatThrownBy(() -> service.checkPasswordExpired(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be positive");
        assertThatThrownBy(() -> service.recordPasswordChange(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be positive");
        assertThatThrownBy(() -> service.getRemainingDays(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be positive");
        assertThat(redis.values).isEmpty();
    }

    @Test
    void malformedRedisTimestampIsResetWithoutLeakingNumberFormatException() {
        PasswordExpireService service = new PasswordExpireService(redis, 3);
        String key = PASSWORD_UPDATE_PREFIX + 7;
        redis.put(key, "dirty-value");

        service.checkPasswordExpired(7L);

        assertThat(redis.get(key)).matches("\\d+");
        assertThat(service.getRemainingDays(7L)).isBetween(0L, 3L);
    }

    @Test
    void expiredPasswordThrowsBusinessException() {
        PasswordExpireService service = new PasswordExpireService(redis, 1);
        String key = PASSWORD_UPDATE_PREFIX + 9;
        redis.put(key, String.valueOf(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)));

        assertThatThrownBy(() -> service.checkPasswordExpired(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码已过期");
    }

    @Test
    void redisFailuresDuringPasswordExpirationCheckFailClosed() {
        PasswordExpireService service = new PasswordExpireService(new ThrowingRedisTemplate(), 30);

        assertThatThrownBy(() -> service.checkPasswordExpired(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码过期策略服务暂不可用，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.SERVICE_ERROR.getCode());
    }

    @Test
    void redisFailuresDuringPasswordChangeRecordFailClosed() {
        PasswordExpireService service = new PasswordExpireService(new ThrowingRedisTemplate(), 30);

        assertThatThrownBy(() -> service.recordPasswordChange(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码过期策略服务暂不可用，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.SERVICE_ERROR.getCode());
    }

    @Test
    void redisFailuresDuringRemainingDaysQueryFallbackToZero() {
        PasswordExpireService service = new PasswordExpireService(new ThrowingRedisTemplate(), 30);

        assertThat(service.getRemainingDays(1L)).isZero();
    }

    private static final class InMemoryRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values = new ConcurrentHashMap<>();

        void put(String key, String value) {
            values.put(key, value);
        }

        String get(String key) {
            return values.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("set".equals(method.getName())) {
                            values.put((String) args[0], (String) args[1]);
                            return null;
                        }
                        if ("get".equals(method.getName())) {
                            return values.get((String) args[0]);
                        }
                        return defaultValue(method.getReturnType());
                    });
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

    private static final class ThrowingRedisTemplate extends StringRedisTemplate {

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        throw new IllegalStateException("redis unavailable");
                    });
        }
    }
}
