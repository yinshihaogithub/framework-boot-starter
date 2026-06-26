package com.framework.redis.service;

import com.framework.redis.config.RedisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisServiceTest {

    @Test
    void tryLockRejectsBlankKeyBeforeWritingRedis() {
        RecordingRedis redis = new RecordingRedis();
        RedisService service = new RedisService(redis, properties());

        assertThatThrownBy(() -> service.tryLock(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key must not be blank");

        assertThat(redis.setIfAbsentKeys).isEmpty();
    }

    @Test
    void trimsKeysAndTokensBeforeRedisAccess() {
        RecordingRedis redis = new RecordingRedis();
        redis.unlockResult = 1L;
        RedisService service = new RedisService(redis, properties());

        assertThat(service.tryLock(" lock:order:1 ")).isNotBlank();
        assertThat(service.unlock(" lock:order:1 ", " token-1 ")).isTrue();

        assertThat(redis.setIfAbsentKeys).containsExactly("lock:order:1");
        assertThat(redis.scriptKeys).containsExactly(List.of("lock:order:1"));
        assertThat(redis.scriptArgs).containsExactly("token-1");
    }

    @Test
    void unlockUsesAtomicScriptAndDoesNotDeleteDirectly() {
        RecordingRedis redis = new RecordingRedis();
        redis.unlockResult = 1L;
        RedisService service = new RedisService(redis, properties());

        assertThat(service.unlock("lock:order:1", "token-1")).isTrue();

        assertThat(redis.scriptKeys).containsExactly(List.of("lock:order:1"));
        assertThat(redis.scriptArgs).containsExactly("token-1");
        assertThat(redis.directDeletes).isEmpty();
        assertThat(redis.valueReads).isEmpty();
    }

    @Test
    void unlockReturnsFalseWhenTokenDoesNotMatch() {
        RecordingRedis redis = new RecordingRedis();
        redis.unlockResult = 0L;
        RedisService service = new RedisService(redis, properties());

        assertThat(service.unlock("lock:order:1", "token-1")).isFalse();
    }

    @Test
    void rejectsBlankUnlockTokenBeforeRedisAccess() {
        RecordingRedis redis = new RecordingRedis();
        RedisService service = new RedisService(redis, properties());

        assertThatThrownBy(() -> service.unlock("lock:order:1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token must not be blank");

        assertThat(redis.scriptKeys).isEmpty();
    }

    private static RedisProperties properties() {
        RedisProperties properties = new RedisProperties();
        properties.setDefaultTtl(Duration.ofMinutes(5));
        properties.setLockTtl(Duration.ofSeconds(30));
        return properties;
    }

    private static final class RecordingRedis extends StringRedisTemplate {
        private final List<String> setIfAbsentKeys = new ArrayList<>();
        private final List<String> directDeletes = new ArrayList<>();
        private final List<String> valueReads = new ArrayList<>();
        private final List<List<String>> scriptKeys = new ArrayList<>();
        private final List<Object> scriptArgs = new ArrayList<>();
        private Long unlockResult = 0L;

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("setIfAbsent".equals(method.getName())) {
                            setIfAbsentKeys.add((String) args[0]);
                            return true;
                        }
                        if ("get".equals(method.getName())) {
                            valueReads.add((String) args[0]);
                            return "token-1";
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        @Override
        public Boolean delete(String key) {
            directDeletes.add(key);
            return true;
        }

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            scriptKeys.add(List.copyOf(keys));
            scriptArgs.add(args[0]);
            return (T) unlockResult;
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
