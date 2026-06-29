package com.framework.auth.service;

import com.framework.core.exception.AuthException;
import com.framework.core.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 登录安全服务
 * - 登录失败计数与锁定
 * - 密码强度校验
 * - 密码过期策略
 */
@Slf4j
public class LoginSecurityService {

    private static final String LOGIN_FAIL_PREFIX = "framework:login:fail:";
    private static final String ACCOUNT_LOCK_PREFIX = "framework:login:lock:";
    private static final String SECURITY_UNAVAILABLE_MESSAGE = "登录安全服务暂不可用，请稍后重试";

    private final StringRedisTemplate redis;
    private final int maxFailCount;          // 最大失败次数
    private final long lockDurationMinutes;  // 锁定时长（分钟）

    public LoginSecurityService(StringRedisTemplate redis, int maxFailCount, long lockDurationMinutes) {
        this.redis = redis;
        this.maxFailCount = maxFailCount;
        this.lockDurationMinutes = lockDurationMinutes;
    }

    /**
     * 检查账号是否被锁定
     */
    public void checkAccountLocked(String username) {
        String normalizedUsername = normalizeUsername(username);
        String lockKey = ACCOUNT_LOCK_PREFIX + normalizedUsername;
        boolean locked;
        Long ttl;
        try {
            locked = Boolean.TRUE.equals(redis.hasKey(lockKey));
            ttl = locked ? redis.getExpire(lockKey, TimeUnit.MINUTES) : 0L;
        } catch (RuntimeException e) {
            throw loginSecurityUnavailable("检查账号锁定状态", normalizedUsername, e);
        }
        if (locked) {
            throw new AuthException(ResultCode.ACCOUNT_LOCKED,
                    "账号已被锁定，请 " + (ttl != null ? ttl : 0) + " 分钟后再试");
        }
    }

    /**
     * 记录登录失败
     * 达到最大失败次数时锁定账号
     */
    public void recordLoginFailure(String username) {
        String normalizedUsername = normalizeUsername(username);
        String failKey = LOGIN_FAIL_PREFIX + normalizedUsername;
        Long count;
        try {
            count = redis.opsForValue().increment(failKey);
            redis.expire(failKey, 30, TimeUnit.MINUTES); // 30分钟内累计

            if (count != null && count >= maxFailCount) {
                String lockKey = ACCOUNT_LOCK_PREFIX + normalizedUsername;
                redis.opsForValue().set(lockKey, "1", lockDurationMinutes, TimeUnit.MINUTES);
                redis.delete(failKey);
            }
        } catch (RuntimeException e) {
            throw loginSecurityUnavailable("记录登录失败", normalizedUsername, e);
        }

        if (count != null && count >= maxFailCount) {
            log.warn("[登录锁定] username={}, 失败{}次, 锁定{}分钟", normalizedUsername, count, lockDurationMinutes);
            throw new AuthException(ResultCode.ACCOUNT_LOCKED,
                    "密码错误次数过多，账号已被锁定 " + lockDurationMinutes + " 分钟");
        }
        log.warn("[登录失败] username={}, 当前失败次数={}/{}", normalizedUsername, count, maxFailCount);
    }

    /**
     * 登录成功时清除失败计数
     */
    public void clearLoginFailure(String username) {
        String normalizedUsername = normalizeUsername(username);
        try {
            redis.delete(LOGIN_FAIL_PREFIX + normalizedUsername);
        } catch (RuntimeException e) {
            log.warn("[登录安全] 清除登录失败计数失败 username={} error={}", normalizedUsername, e.getMessage());
        }
    }

    /**
     * 获取当前失败次数
     */
    public long getFailCount(String username) {
        String normalizedUsername = normalizeUsername(username);
        try {
            String count = redis.opsForValue().get(LOGIN_FAIL_PREFIX + normalizedUsername);
            return count != null ? Long.parseLong(count) : 0;
        } catch (RuntimeException e) {
            log.warn("[登录安全] 查询登录失败次数失败 username={} error={}", normalizedUsername, e.getMessage());
            return 0;
        }
    }

    /**
     * 获取账号登录安全状态。
     */
    public LoginSecurityStatus getStatus(String username) {
        String normalizedUsername = normalizeUsername(username);
        String lockKey = ACCOUNT_LOCK_PREFIX + normalizedUsername;
        try {
            boolean locked = Boolean.TRUE.equals(redis.hasKey(lockKey));
            Long ttl = locked ? redis.getExpire(lockKey, TimeUnit.MINUTES) : 0L;
            return new LoginSecurityStatus(
                    getFailCount(normalizedUsername),
                    locked,
                    ttl == null || ttl < 0 ? 0L : ttl);
        } catch (RuntimeException e) {
            log.warn("[登录安全] 查询账号登录安全状态失败 username={} error={}", normalizedUsername, e.getMessage());
            return new LoginSecurityStatus(0, false, 0);
        }
    }

    /**
     * 强制解锁账号
     */
    public void unlock(String username) {
        String normalizedUsername = normalizeUsername(username);
        try {
            redis.delete(ACCOUNT_LOCK_PREFIX + normalizedUsername);
            redis.delete(LOGIN_FAIL_PREFIX + normalizedUsername);
        } catch (RuntimeException e) {
            throw loginSecurityUnavailable("解锁账号", normalizedUsername, e);
        }
        log.info("[账号解锁] username={}", normalizedUsername);
    }

    private AuthException loginSecurityUnavailable(String action, String username, RuntimeException e) {
        log.error("[登录安全] {}失败 username={} error={}", action, username, e.getMessage());
        return new AuthException(ResultCode.SERVICE_ERROR, SECURITY_UNAVAILABLE_MESSAGE);
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? null : username.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return normalized;
    }

    public record LoginSecurityStatus(long failCount, boolean locked, long lockTtlMinutes) {
    }
}
