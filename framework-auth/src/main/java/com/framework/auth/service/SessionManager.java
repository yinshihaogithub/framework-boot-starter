package com.framework.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.auth.context.LoginUser;
import com.framework.auth.support.AuthTextSupport;
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
import java.util.PriorityQueue;
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
    private static final int SESSION_FIELD_MAX_LENGTH = 128;
    private static final int DEFAULT_SESSION_PAGE_NUM = 1;
    private static final int DEFAULT_SESSION_PAGE_SIZE = 20;
    private static final int MAX_SESSION_PAGE_SIZE = 1000;

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
        if (userId == null) {
            throw new AuthException(ResultCode.PARAM_ERROR, "userId不能为空");
        }
        String safeUsername = requireSessionText(username, "username");
        String safeTenantId = requireSessionText(tenantId, "tenantId");
        String safeDeviceId = requireSessionText(deviceId, "deviceId");
        String[] safeRoles = normalizeArray(roles);
        String[] safePermissions = normalizeArray(permissions);

        // 多端互踢：踢掉同一用户的旧会话
        kickOutOldSession(userId, safeDeviceId);

        String accessToken = jwtUtils.generateAccessToken(userId, safeUsername, safeTenantId, safeDeviceId);
        String refreshToken = jwtUtils.generateRefreshToken(userId, safeDeviceId);

        // 会话存 Redis
        String sessionKey = buildSessionKey(userId, safeDeviceId);
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("userId", String.valueOf(userId));
        sessionData.put("username", safeUsername);
        sessionData.put("tenantId", safeTenantId);
        sessionData.put("deviceId", safeDeviceId);
        sessionData.put("refreshToken", refreshToken);
        sessionData.put("roles", writeArray(safeRoles));
        sessionData.put("permissions", writeArray(safePermissions));
        sessionData.put("loginTime", String.valueOf(System.currentTimeMillis()));
        persistSession(sessionKey, sessionData);

        LoginUser user = new LoginUser()
                .setUserId(userId)
                .setUsername(safeUsername)
                .setTenantId(safeTenantId)
                .setDeviceId(safeDeviceId)
                .setAccessToken(accessToken)
                .setRoles(safeRoles)
                .setPermissions(safePermissions);
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

        try {
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
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[会话管理] refreshToken 校验失败 userId={} deviceId={} error={}",
                    userId, deviceId, e.getMessage());
            throw new AuthException(ResultCode.SERVICE_ERROR, "会话服务暂不可用，请稍后重试");
        }
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
        deleteSessionKey(buildSessionKey(userId, deviceId), "登出");

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
            if (userId == null || !AuthTextSupport.hasText(deviceId)) {
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
        try {
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
        } catch (Exception e) {
            log.warn("[会话管理] 读取会话失败 key={} error={}", sessionKey, e.getMessage());
            return null;
        }
    }

    /**
     * 获取在线用户数
     * 使用 SCAN 遍历 session keys
     */
    public long getOnlineUserCount() {
        return listOnlineSessionsPage(1, 1).total();
    }

    /**
     * 获取在线会话列表，供管理后台查看与强制下线。
     */
    public List<OnlineSession> listOnlineSessions() {
        return scanKeys(FrameworkConstants.SESSION_PREFIX + "*").stream()
                .map(this::readOnlineSession)
                .filter(SessionManager::isValidOnlineSession)
                .sorted(Comparator.comparingLong(OnlineSession::loginTime).reversed())
                .toList();
    }

    /**
     * 查询指定用户设备会话是否在线，避免管理端强制下线前扫描全部会话。
     */
    public boolean hasOnlineSession(Long userId, String deviceId) {
        String safeDeviceId = normalizeTargetDeviceId(userId, deviceId);
        if (safeDeviceId == null) {
            return false;
        }
        OnlineSession session = readOnlineSession(buildSessionKey(userId, safeDeviceId));
        return isValidOnlineSession(session)
                && userId.equals(session.userId())
                && safeDeviceId.equals(session.deviceId());
    }

    /**
     * 分页获取在线会话，扫描时只保留当前页所需的最新窗口，避免管理端全量加载后排序。
     */
    public OnlineSessionPage listOnlineSessionsPage(int pageNum, int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        long offset = (long) (safePageNum - 1) * safePageSize;
        long keepSize = Math.min(offset + safePageSize, Integer.MAX_VALUE);
        PriorityQueue<OnlineSession> newestWindow = new PriorityQueue<>(SessionManager::compareOldestFirst);
        long total = 0L;
        String pattern = FrameworkConstants.SESSION_PREFIX + "*";

        try {
            Cursor<String> cursor = scan(pattern);
            if (cursor == null) {
                return new OnlineSessionPage(List.of(), 0L, safePageNum, safePageSize);
            }
            try {
                while (cursor.hasNext()) {
                    OnlineSession session = readOnlineSession(cursor.next());
                    if (!isValidOnlineSession(session)) {
                        continue;
                    }
                    total++;
                    keepNewestSession(newestWindow, session, keepSize);
                }
            } finally {
                closeCursor(cursor, pattern);
            }
        } catch (Exception e) {
            log.warn("[会话管理] 分页扫描会话失败 pattern={} error={}", pattern, e.getMessage());
            return new OnlineSessionPage(List.of(), 0L, safePageNum, safePageSize);
        }

        List<OnlineSession> records = newestWindow.stream()
                .sorted(SessionManager::compareNewestFirst)
                .skip(offset)
                .limit(safePageSize)
                .toList();
        return new OnlineSessionPage(records, total, safePageNum, safePageSize);
    }

    /**
     * 强制下线
     */
    public void forceLogout(Long userId, String deviceId) {
        String safeDeviceId = normalizeTargetDeviceId(userId, deviceId);
        if (safeDeviceId == null) {
            return;
        }
        deleteSessionKey(buildSessionKey(userId, safeDeviceId), "强制下线");
        log.info("[强制下线] userId={}, deviceId={}", userId, safeDeviceId);
    }

    /**
     * 强制下线指定用户的全部设备会话。
     */
    public void forceLogoutAll(Long userId) {
        if (userId == null || userId <= 0) {
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
        try {
            if (Boolean.TRUE.equals(redis.hasKey(sessionKey))) {
                // 旧会话存在，删除并记录日志
                redis.delete(sessionKey);
                log.info("[多端互踢] userId={}, deviceId={} 旧会话已踢下线", userId, deviceId);
            }
        } catch (Exception e) {
            log.warn("[会话管理] 多端互踢失败 userId={} deviceId={} error={}",
                    userId, deviceId, e.getMessage());
        }
    }

    private void persistSession(String sessionKey, Map<String, String> sessionData) {
        try {
            redis.opsForHash().putAll(sessionKey, sessionData);
            redis.expire(sessionKey, sessionTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[会话管理] 创建会话失败 key={} error={}", sessionKey, e.getMessage());
            throw new AuthException(ResultCode.SERVICE_ERROR, "会话服务暂不可用，请稍后重试");
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
        try {
            return Boolean.TRUE.equals(redis.hasKey(FrameworkConstants.TOKEN_BLACKLIST_PREFIX + token));
        } catch (Exception e) {
            log.warn("[会话管理] Token黑名单检查失败 error={}", e.getMessage());
            return true;
        }
    }

    private long deleteSessionKeys(String pattern) {
        List<String> keys = scanKeys(pattern);
        if (keys.isEmpty()) {
            return 0;
        }
        try {
            Long deleted = redis.delete(keys);
            return deleted == null ? 0 : deleted;
        } catch (Exception e) {
            log.warn("[会话管理] 批量删除会话失败 pattern={} error={}", pattern, e.getMessage());
            return 0;
        }
    }

    private void deleteSessionKey(String sessionKey, String action) {
        try {
            redis.delete(sessionKey);
        } catch (Exception e) {
            log.warn("[会话管理] {}删除会话失败 key={} error={}", action, sessionKey, e.getMessage());
        }
    }

    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        try {
            Cursor<String> cursor = scan(pattern);
            if (cursor == null) {
                return keys;
            }
            try {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
                return keys;
            } finally {
                closeCursor(cursor, pattern);
            }
        } catch (Exception e) {
            log.warn("[会话管理] 扫描会话失败 pattern={} error={}", pattern, e.getMessage());
            return List.of();
        }
    }

    private Cursor<String> scan(String pattern) {
        return redis.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(1000)
                .build());
    }

    private void closeCursor(Cursor<String> cursor, String pattern) {
        try {
            cursor.close();
        } catch (Exception e) {
            log.warn("[会话管理] 关闭扫描游标失败 pattern={} error={}", pattern, e.getMessage());
        }
    }

    private String buildSessionKey(Long userId, String deviceId) {
        return FrameworkConstants.SESSION_PREFIX + userId + ":" + deviceId;
    }

    private String normalizeTargetDeviceId(Long userId, String deviceId) {
        if (userId == null || userId <= 0 || !AuthTextSupport.hasText(deviceId)) {
            return null;
        }
        return AuthTextSupport.trimBoundarySpace(deviceId);
    }

    private String requireSessionText(String value, String fieldName) {
        if (!AuthTextSupport.hasText(value)) {
            throw new AuthException(ResultCode.PARAM_ERROR, fieldName + "不能为空");
        }
        String normalized = AuthTextSupport.trimBoundarySpace(value);
        if (normalized.length() > SESSION_FIELD_MAX_LENGTH) {
            throw new AuthException(ResultCode.PARAM_ERROR, fieldName + "长度不能超过128个字符");
        }
        return normalized;
    }

    private String[] normalizeArray(String[] values) {
        if (values == null || values.length == 0) {
            return new String[0];
        }
        List<String> normalized = new ArrayList<>(values.length);
        for (String value : values) {
            String normalizedValue = AuthTextSupport.trimToNull(value);
            if (normalizedValue != null) {
                normalized.add(normalizedValue);
            }
        }
        return normalized.toArray(String[]::new);
    }

    private OnlineSession readOnlineSession(String sessionKey) {
        try {
            Map<Object, Object> data = redis.opsForHash().entries(sessionKey);
            Long ttlSeconds = redis.getExpire(sessionKey, TimeUnit.SECONDS);
            return new OnlineSession(
                    parseLong(data.get("userId")),
                    asString(data.get("username")),
                    asString(data.get("tenantId")),
                    asString(data.get("deviceId")),
                    parseLongOrDefault(data.get("loginTime"), 0L),
                    ttlSeconds == null ? -1L : ttlSeconds);
        } catch (Exception e) {
            log.warn("[会话管理] 读取在线会话失败 key={} error={}", sessionKey, e.getMessage());
            return new OnlineSession(null, null, null, null, 0L, -1L);
        }
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
            return normalizeArray(OBJECT_MAPPER.readValue(String.valueOf(value), String[].class));
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

    private static int safePageNum(int pageNum) {
        return pageNum <= 0 ? DEFAULT_SESSION_PAGE_NUM : pageNum;
    }

    private static int safePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_SESSION_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_SESSION_PAGE_SIZE);
    }

    private static boolean isValidOnlineSession(OnlineSession session) {
        return session.userId() != null && AuthTextSupport.hasText(session.deviceId());
    }

    private static void keepNewestSession(PriorityQueue<OnlineSession> sessions,
                                          OnlineSession candidate,
                                          long keepSize) {
        if (keepSize <= 0) {
            return;
        }
        if (sessions.size() < keepSize) {
            sessions.offer(candidate);
            return;
        }
        OnlineSession oldest = sessions.peek();
        if (oldest != null && compareNewestFirst(candidate, oldest) < 0) {
            sessions.poll();
            sessions.offer(candidate);
        }
    }

    private static int compareNewestFirst(OnlineSession left, OnlineSession right) {
        int byLoginTime = Long.compare(right.loginTime(), left.loginTime());
        if (byLoginTime != 0) {
            return byLoginTime;
        }
        int byUserId = Long.compare(nullSafe(right.userId()), nullSafe(left.userId()));
        if (byUserId != 0) {
            return byUserId;
        }
        return nullSafe(right.deviceId()).compareTo(nullSafe(left.deviceId()));
    }

    private static int compareOldestFirst(OnlineSession left, OnlineSession right) {
        return compareNewestFirst(right, left);
    }

    private static long nullSafe(Long value) {
        return value == null ? Long.MIN_VALUE : value;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public record OnlineSession(Long userId, String username, String tenantId, String deviceId,
                                long loginTime, long ttlSeconds) {
    }

    public record OnlineSessionPage(List<OnlineSession> records,
                                    long total,
                                    int pageNum,
                                    int pageSize) {
    }
}
