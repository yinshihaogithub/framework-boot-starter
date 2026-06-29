package com.framework.auth.service;

import com.framework.auth.context.LoginUser;
import com.framework.auth.jwt.JwtUtils;
import com.framework.core.constant.FrameworkConstants;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Test
    void forceLogoutAllRemovesEverySessionOfUser() {
        LoginUser web = sessionManager.createSession(1L, "alice", "tenant-a", "web",
                new String[]{"ADMIN"}, new String[]{"user:view"});
        LoginUser app = sessionManager.createSession(1L, "alice", "tenant-a", "app",
                new String[]{"ADMIN"}, new String[]{"user:view"});
        LoginUser other = sessionManager.createSession(2L, "bob", "tenant-a", "web",
                new String[]{"USER"}, new String[]{"profile:view"});

        sessionManager.forceLogoutAll(1L);

        assertThat(sessionManager.validateAccessToken(web.getAccessToken())).isFalse();
        assertThat(sessionManager.validateAccessToken(app.getAccessToken())).isFalse();
        assertThat(sessionManager.validateAccessToken(other.getAccessToken())).isTrue();
    }

    @Test
    void forceLogoutAllWithoutUserRemovesEverySession() {
        LoginUser alice = sessionManager.createSession(1L, "alice", "tenant-a", "web",
                new String[]{"ADMIN"}, new String[]{"user:view"});
        LoginUser bob = sessionManager.createSession(2L, "bob", "tenant-a", "web",
                new String[]{"USER"}, new String[]{"profile:view"});

        sessionManager.forceLogoutAll();

        assertThat(sessionManager.validateAccessToken(alice.getAccessToken())).isFalse();
        assertThat(sessionManager.validateAccessToken(bob.getAccessToken())).isFalse();
    }

    @Test
    void listOnlineSessionsReturnsSessionMetadataSortedByLoginTime() throws Exception {
        sessionManager.createSession(1L, "alice", "tenant-a", "web",
                new String[]{"ADMIN"}, new String[]{"user:view"});
        Thread.sleep(2L);
        sessionManager.createSession(2L, "bob", "tenant-b", "mobile",
                new String[]{"USER"}, new String[]{"profile:view"});

        List<SessionManager.OnlineSession> sessions = sessionManager.listOnlineSessions();

        assertThat(sessionManager.getOnlineUserCount()).isEqualTo(2);
        assertThat(sessions)
                .extracting(SessionManager.OnlineSession::userId,
                        SessionManager.OnlineSession::username,
                        SessionManager.OnlineSession::tenantId,
                        SessionManager.OnlineSession::deviceId,
                        SessionManager.OnlineSession::ttlSeconds)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2L, "bob", "tenant-b", "mobile", 3600L),
                        org.assertj.core.groups.Tuple.tuple(1L, "alice", "tenant-a", "web", 3600L));
        assertThat(sessions.get(0).loginTime()).isGreaterThanOrEqualTo(sessions.get(1).loginTime());
    }

    private static final class InMemoryRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final Map<String, Map<Object, Object>> hashes = new ConcurrentHashMap<>();
        private final Map<String, Long> ttlSeconds = new ConcurrentHashMap<>();

        @Override
        public Boolean hasKey(String key) {
            return values.containsKey(key) || hashes.containsKey(key);
        }

        @Override
        public Boolean expire(String key, long timeout, TimeUnit unit) {
            if (!Boolean.TRUE.equals(hasKey(key))) {
                return false;
            }
            ttlSeconds.put(key, unit.toSeconds(timeout));
            return true;
        }

        @Override
        public Long getExpire(String key, TimeUnit timeUnit) {
            Long seconds = ttlSeconds.get(key);
            if (seconds == null) {
                return -1L;
            }
            return timeUnit.convert(seconds, TimeUnit.SECONDS);
        }

        @Override
        public Boolean delete(String key) {
            boolean removedValue = values.remove(key) != null;
            boolean removedHash = hashes.remove(key) != null;
            ttlSeconds.remove(key);
            return removedValue || removedHash;
        }

        @Override
        public Long delete(Collection<String> keys) {
            long deleted = 0;
            for (String key : keys) {
                if (Boolean.TRUE.equals(delete(key))) {
                    deleted++;
                }
            }
            return deleted;
        }

        @Override
        public Cursor<String> scan(ScanOptions options) {
            String pattern = options == null ? "*" : options.getPattern();
            List<String> keys = new ArrayList<>();
            values.keySet().stream()
                    .filter(key -> matches(pattern, key))
                    .forEach(keys::add);
            hashes.keySet().stream()
                    .filter(key -> matches(pattern, key))
                    .forEach(keys::add);
            return new ListCursor(keys);
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

        private static boolean matches(String pattern, String key) {
            if (pattern == null || "*".equals(pattern)) {
                return true;
            }
            String regex = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*");
            return key.matches(regex);
        }
    }

    private static final class ListCursor implements Cursor<String> {
        private final Iterator<String> iterator;
        private boolean closed;
        private long position;

        private ListCursor(List<String> values) {
            this.iterator = values.iterator();
        }

        @Override
        public long getCursorId() {
            return 0;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public long getPosition() {
            return position;
        }

        @Override
        public boolean hasNext() {
            return !closed && iterator.hasNext();
        }

        @Override
        public String next() {
            position++;
            return iterator.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
