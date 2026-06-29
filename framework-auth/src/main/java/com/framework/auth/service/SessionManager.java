package com.framework.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.auth.context.LoginUser;
import com.framework.auth.jwt.JwtUtils;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.exception.AuthException;
import com.framework.core.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理服务
 * - 登录/登出
 * - Token 刷新
 * - 会话持久化到 Redis
 * - 多端互踢
 * - Token 黑名单
 */
@Slf4j
public class SessionManager {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;
    private final JwtUtils jwtUtils;
    private final long sessionTimeoutSeconds;

    public SessionManager(StringRedisTemplate redis, JwtUtils jwtUtils, long sessionTimeoutSeconds) {
        this.redis = redis;
        this.jwtUtils = jwtUtils;
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
    }

    /**
     * 创建会话（登录成功后调用）
     */
    public LoginUser createSession(Long userId, String username, String tenantId, String deviceId,
                                   String[] roles, String[] permissions) {
        // 多端互踢：踢掉同一用户的旧会话
        kickOutOldSession(userId, deviceId);

        String accessToken = jwtUtils.generateAccessToken(userId, username, tenantId, deviceId);
        String refreshToken = jwtUtils.generateRefreshToken(userId, deviceId);

        // 会话存 Redis
        String sessionKey = buildSessionKey(userId, deviceId);
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("userId", String.valueOf(userId));
        sessionData.put("username", username);
        sessionData.put("tenantId", tenantId);
        sessionData.put("deviceId", deviceId);
        sessionData.put("refreshToken", refreshToken);
        sessionData.put("roles", writeArray(roles));
        sessionData.put("permissions", writeArray(permissions));
        sessionData.put("loginTime", String.valueOf(System.currentTimeMillis()));
        redis.opsForHash().putAll(sessionKey, sessionData);
        redis.expire(sessionKey, sessionTimeoutSeconds, TimeUnit.SECONDS);

        LoginUser user = new LoginUser()
                .setUserId(userId)
                .setUsername(username)
                .setTenantId(tenantId)
                .setDeviceId(deviceId)
                .setAccessToken(accessToken)
                .setRoles(roles)
                .setPermissions(permissions);
        return user;
    }

    /**
     * 刷新 Token
     */
    public String refreshToken(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new AuthException(ResultCode.TOKEN_EXPIRED, "refreshToken无效或已过期");
        }
        if (!"refresh".equals(jwtUtils.getTokenType(refreshToken))) {
            throw new AuthException("Token类型错误");
        }
        Long userId = jwtUtils.getUserId(refreshToken);
        String deviceId = jwtUtils.getDeviceId(refreshToken);

        // 校验会话是否存在
        String sessionKey = buildSessionKey(userId, deviceId);
        if (Boolean.FALSE.equals(redis.hasKey(sessionKey))) {
            throw new AuthException(ResultCode.TOKEN_EXPIRED, "会话已失效，请重新登录");
        }

        // 校验 refreshToken 是否匹配
        Object storedRefresh = redis.opsForHash().get(sessionKey, "refreshToken");
        if (!refreshToken.equals(storedRefresh)) {
            throw new AuthException("refreshToken不匹配");
        }

        // 生成新 accessToken
        String username = (String) redis.opsForHash().get(sessionKey, "username");
        String tenantId = (String) redis.opsForHash().get(sessionKey, "tenantId");
        String newAccessToken = jwtUtils.generateAccessToken(userId, username, tenantId, deviceId);

        // 续期会话
        redis.expire(sessionKey, sessionTimeoutSeconds, TimeUnit.SECONDS);

        return newAccessToken;
    }

    /**
     * 注销（登出）
     */
    public void logout(String accessToken) {
        if (!jwtUtils.validateToken(accessToken)) {
            return;
        }
        Long userId = jwtUtils.getUserId(accessToken);
        String deviceId = jwtUtils.getDeviceId(accessToken);

        // 删除会话
        redis.delete(buildSessionKey(userId, deviceId));

        // accessToken 加入黑名单（TTL = 剩余有效期）
        addToBlacklist(accessToken);

        log.info("[登出] userId={}, deviceId={}", userId, deviceId);
    }

    /**
     * 验证 accessToken 是否有效（含黑名单检查）
     */
    public boolean validateAccessToken(String accessToken) {
        try {
            if (!jwtUtils.validateToken(accessToken)) {
                return false;
            }
            if (isInBlacklist(accessToken)) {
                return false;
            }
            if (!"access".equals(jwtUtils.getTokenType(accessToken))) {
                return false;
            }
            Long userId = jwtUtils.getUserId(accessToken);
            String deviceId = jwtUtils.getDeviceId(accessToken);
            if (userId == null || deviceId == null || deviceId.isBlank()) {
                return false;
            }
            return Boolean.TRUE.equals(redis.hasKey(buildSessionKey(userId, deviceId)));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从有效 accessToken 恢复当前登录用户。
     */
    public LoginUser getLoginUser(String accessToken) {
        if (!validateAccessToken(accessToken)) {
            return null;
        }
        Long userId = jwtUtils.getUserId(accessToken);
        String deviceId = jwtUtils.getDeviceId(accessToken);
        String sessionKey = buildSessionKey(userId, deviceId);
        Map<Object, Object> sessionData = redis.opsForHash().entries(sessionKey);
        if (sessionData == null || sessionData.isEmpty()) {
            return null;
        }
        return new LoginUser()
                .setUserId(userId)
                .setUsername(asString(sessionData.get("username")))
                .setTenantId(asString(sessionData.get("tenantId")))
                .setDeviceId(deviceId)
                .setAccessToken(accessToken)
                .setRoles(readArray(sessionData.get("roles")))
                .setPermissions(readArray(sessionData.get("permissions")));
    }

    /**
     * 获取在线用户数
     * 使用 SCAN 遍历 session keys
     */
    public long getOnlineUserCount() {
        return listOnlineSessions().size();
    }

    /**
     * 获取在线会话列表，供管理后台查看与强制下线。
     */
    public List<OnlineSession> listOnlineSessions() {
        return scanKeys(FrameworkConstants.SESSION_PREFIX + "*").stream()
                .map(this::readOnlineSession)
                .filter(session -> session.userId() != null && hasText(session.deviceId()))
                .sorted(Comparator.comparingLong(OnlineSession::loginTime).reversed())
                .toList();
    }

    /**
     * 强制下线
     */
    public void forceLogout(Long userId, String deviceId) {
        redis.delete(buildSessionKey(userId, deviceId));
        log.info("[强制下线] userId={}, deviceId={}", userId, deviceId);
    }

    /**
     * 强制下线指定用户的全部设备会话。
     */
    public void forceLogoutAll(Long userId) {
        if (userId == null) {
            return;
        }
        long count = deleteSessionKeys(FrameworkConstants.SESSION_PREFIX + userId + ":*");
        log.info("[强制下线] userId={}, count={}", userId, count);
    }

    /**
     * 强制下线全部在线会话，适用于菜单权限模型变更。
     */
    public void forceLogoutAll() {
        long count = deleteSessionKeys(FrameworkConstants.SESSION_PREFIX + "*");
        log.info("[强制下线] allSessions=true, count={}", count);
    }

    // ===== 私有方法 =====

    private void kickOutOldSession(Long userId, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceId);
        if (Boolean.TRUE.equals(redis.hasKey(sessionKey))) {
            // 旧会话存在，删除并记录日志
            redis.delete(sessionKey);
            log.info("[多端互踢] userId={}, deviceId={} 旧会话已踢下线", userId, deviceId);
        }
    }

    private void addToBlacklist(String token) {
        try {
            long expireMs = jwtUtils.parseToken(token).getExpiration().getTime() - System.currentTimeMillis();
            if (expireMs > 0) {
                redis.opsForValue().set(
                        FrameworkConstants.TOKEN_BLACKLIST_PREFIX + token,
                        "1", expireMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("加入Token黑名单失败: {}", e.getMessage());
        }
    }

    private boolean isInBlacklist(String token) {
        return Boolean.TRUE.equals(redis.hasKey(FrameworkConstants.TOKEN_BLACKLIST_PREFIX + token));
    }

    private long deleteSessionKeys(String pattern) {
        List<String> keys = scanKeys(pattern);
        if (keys.isEmpty()) {
            return 0;
        }
        Long deleted = redis.delete(keys);
        return deleted == null ? 0 : deleted;
    }

    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(1000)
                .build());
        if (cursor == null) {
            return keys;
        }
        try {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
            return keys;
        } finally {
            cursor.close();
        }
    }

    private String buildSessionKey(Long userId, String deviceId) {
        return FrameworkConstants.SESSION_PREFIX + userId + ":" + deviceId;
    }

    private OnlineSession readOnlineSession(String sessionKey) {
        Map<Object, Object> data = redis.opsForHash().entries(sessionKey);
        Long ttlSeconds = redis.getExpire(sessionKey, TimeUnit.SECONDS);
        return new OnlineSession(
                parseLong(data.get("userId")),
                asString(data.get("username")),
                asString(data.get("tenantId")),
                asString(data.get("deviceId")),
                parseLongOrDefault(data.get("loginTime"), 0L),
                ttlSeconds == null ? -1L : ttlSeconds);
    }

    private String writeArray(String[] values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values != null ? values : new String[0]);
        } catch (Exception e) {
            throw new AuthException(ResultCode.TOKEN_INVALID, "会话权限数据序列化失败");
        }
    }

    private String[] readArray(Object value) {
        if (value == null) {
            return new String[0];
        }
        try {
            return OBJECT_MAPPER.readValue(String.valueOf(value), String[].class);
        } catch (Exception e) {
            log.warn("会话权限数据反序列化失败: {}", e.getMessage());
            return new String[0];
        }
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long parseLongOrDefault(Object value, long defaultValue) {
        Long parsed = parseLong(value);
        return parsed == null ? defaultValue : parsed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record OnlineSession(Long userId, String username, String tenantId, String deviceId,
                                long loginTime, long ttlSeconds) {
    }
}
