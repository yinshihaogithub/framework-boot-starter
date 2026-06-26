package com.framework.auth.service;

import com.framework.core.exception.AuthException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginSecurityServiceTest {

    @Test
    void trimsUsernameBeforeUsingRedisKeys() {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        LoginSecurityService service = new LoginSecurityService(redis, 5, 30);
        redis.values.put("framework:login:lock:alice", "1");

        assertThatThrownBy(() -> service.checkAccountLocked(" alice "))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("账号已被锁定");

        service.recordLoginFailure(" alice ");
        service.clearLoginFailure(" alice ");
        service.unlock(" alice ");

        assertThat(redis.incrementKeys).containsExactly("framework:login:fail:alice");
        assertThat(redis.expireKeys).containsExactly("framework:login:fail:alice");
        assertThat(redis.deletedKeys).containsExactly(
                "framework:login:fail:alice",
                "framework:login:lock:alice",
                "framework:login:fail:alice"
        );
    }

    @Test
    void rejectsBlankUsernameBeforeUsingRedis() {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        LoginSecurityService service = new LoginSecurityService(redis, 5, 30);

        assertThatThrownBy(() -> service.checkAccountLocked(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username must not be blank");
        assertThatThrownBy(() -> service.recordLoginFailure(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username must not be blank");
        assertThatThrownBy(() -> service.clearLoginFailure(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username must not be blank");
        assertThatThrownBy(() -> service.getFailCount(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username must not be blank");
        assertThatThrownBy(() -> service.unlock(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username must not be blank");

        assertThat(redis.incrementKeys).isEmpty();
        assertThat(redis.deletedKeys).isEmpty();
        assertThat(redis.expireKeys).isEmpty();
    }

    private static final class RecordingRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final List<String> incrementKeys = new ArrayList<>();
        private final List<String> expireKeys = new ArrayList<>();
        private final List<String> deletedKeys = new ArrayList<>();

        @Override
        public Boolean hasKey(String key) {
            return values.containsKey(key);
        }

        @Override
        public Long getExpire(String key, TimeUnit timeUnit) {
            return values.containsKey(key) ? 5L : -2L;
        }

        @Override
        public Boolean expire(String key, long timeout, TimeUnit unit) {
            expireKeys.add(key);
            return values.containsKey(key);
        }

        @Override
        public Boolean delete(String key) {
            deletedKeys.add(key);
            return values.remove(key) != null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("increment".equals(method.getName())) {
                            String key = (String) args[0];
                            incrementKeys.add(key);
                            long next = Long.parseLong(values.getOrDefault(key, "0")) + 1;
                            values.put(key, String.valueOf(next));
                            return next;
                        }
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
}
