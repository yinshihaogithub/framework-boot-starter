package com.framework.auth.service;

import com.framework.auth.context.LoginUser;
import com.framework.auth.jwt.JwtUtils;
import com.framework.core.constant.FrameworkConstants;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SessionManagerTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-chars";

    private final InMemoryRedisTemplate redis = new InMemoryRedisTemplate();
    private final JwtUtils jwtUtils = new JwtUtils(SECRET, 3600, 86400);
    private final SessionManager sessionManager = new SessionManager(redis, jwtUtils, 3600);

    @Test
    void validateAccessTokenRejectsRefreshToken() {
        sessionManager.createSession(1L, "alice", "tenant-a", "web",
                new String[]{"ADMIN"}, new String[]{"user:view"});
        String refreshToken = jwtUtils.generateRefreshToken(1L, "web");

        assertThat(sessionManager.validateAccessToken(refreshToken)).isFalse();
    }

    @Test
    void validateAccessTokenRejectsDeletedSession() {
        LoginUser user = sessionManager.createSession(1L, "alice", "tenant-a", "web",
                new String[]{"ADMIN"}, new String[]{"user:view"});

        sessionManager.forceLogout(1L, "web");

        assertThat(sessionManager.validateAccessToken(user.getAccessToken())).isFalse();
    }

    @Test
    void getLoginUserRestoresRolesAndPermissionsFromSession() {
        LoginUser user = sessionManager.createSession(1L, "alice", "tenant-a", "web",
                new String[]{"ADMIN", "AUDITOR"}, new String[]{"user:view", "user:edit"});

        LoginUser restored = sessionManager.getLoginUser(user.getAccessToken());

        assertThat(restored.getUserId()).isEqualTo(1L);
        assertThat(restored.getUsername()).isEqualTo("alice");
        assertThat(restored.getTenantId()).isEqualTo("tenant-a");
        assertThat(restored.getDeviceId()).isEqualTo("web");
        assertThat(restored.getAccessToken()).isEqualTo(user.getAccessToken());
        assertThat(restored.getRoles()).containsExactly("ADMIN", "AUDITOR");
        assertThat(restored.getPermissions()).containsExactly("user:view", "user:edit");
    }

    @Test
    void getLoginUserReturnsNullWhenSessionMissing() {
        LoginUser user = sessionManager.createSession(1L, "alice", "tenant-a", "web",
                new String[]{"ADMIN"}, new String[]{"user:view"});
        redis.delete(FrameworkConstants.SESSION_PREFIX + "1:web");

        assertThat(sessionManager.getLoginUser(user.getAccessToken())).isNull();
    }

    private static final class InMemoryRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final Map<String, Map<Object, Object>> hashes = new ConcurrentHashMap<>();

        @Override
        public Boolean hasKey(String key) {
            return values.containsKey(key) || hashes.containsKey(key);
        }

        @Override
        public Boolean expire(String key, long timeout, TimeUnit unit) {
            return hasKey(key);
        }

        @Override
        public Boolean delete(String key) {
            boolean removedValue = values.remove(key) != null;
            boolean removedHash = hashes.remove(key) != null;
            return removedValue || removedHash;
        }

        @Override
        @SuppressWarnings("unchecked")
        public HashOperations<String, Object, Object> opsForHash() {
            return (HashOperations<String, Object, Object>) Proxy.newProxyInstance(
                    HashOperations.class.getClassLoader(),
                    new Class<?>[]{HashOperations.class},
                    (proxy, method, args) -> {
                        if ("putAll".equals(method.getName())) {
                            hashes.put((String) args[0], new LinkedHashMap<>((Map<?, ?>) args[1]));
                            return null;
                        }
                        if ("get".equals(method.getName())) {
                            return hashes.getOrDefault((String) args[0], Map.of()).get(args[1]);
                        }
                        if ("entries".equals(method.getName())) {
                            return new LinkedHashMap<>(hashes.getOrDefault((String) args[0], Map.of()));
                        }
                        return defaultValue(method.getReturnType());
                    });
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
}
