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
        String lockKey = ACCOUNT_LOCK_PREFIX + normalizeUsername(username);
        if (Boolean.TRUE.equals(redis.hasKey(lockKey))) {
            Long ttl = redis.getExpire(lockKey, TimeUnit.MINUTES);
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
        Long count = redis.opsForValue().increment(failKey);
        redis.expire(failKey, 30, TimeUnit.MINUTES); // 30分钟内累计

        if (count != null && count >= maxFailCount) {
            // 锁定账号
            String lockKey = ACCOUNT_LOCK_PREFIX + normalizedUsername;
            redis.opsForValue().set(lockKey, "1", lockDurationMinutes, TimeUnit.MINUTES);
            redis.delete(failKey);
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
        redis.delete(LOGIN_FAIL_PREFIX + normalizeUsername(username));
    }

    /**
     * 获取当前失败次数
     */
    public long getFailCount(String username) {
        String count = redis.opsForValue().get(LOGIN_FAIL_PREFIX + normalizeUsername(username));
        return count != null ? Long.parseLong(count) : 0;
    }

    /**
     * 获取账号登录安全状态。
     */
    public LoginSecurityStatus getStatus(String username) {
        String normalizedUsername = normalizeUsername(username);
        String lockKey = ACCOUNT_LOCK_PREFIX + normalizedUsername;
        boolean locked = Boolean.TRUE.equals(redis.hasKey(lockKey));
        Long ttl = locked ? redis.getExpire(lockKey, TimeUnit.MINUTES) : 0L;
        return new LoginSecurityStatus(
                getFailCount(normalizedUsername),
                locked,
                ttl == null || ttl < 0 ? 0L : ttl);
    }

    /**
     * 强制解锁账号
     */
    public void unlock(String username) {
        String normalizedUsername = normalizeUsername(username);
        redis.delete(ACCOUNT_LOCK_PREFIX + normalizedUsername);
        redis.delete(LOGIN_FAIL_PREFIX + normalizedUsername);
        log.info("[账号解锁] username={}", normalizedUsername);
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
