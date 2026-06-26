package com.framework.auth.service;

import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 密码过期策略服务
 * - 检查密码是否过期
 * - 记录密码修改时间
 */
@Slf4j
public class PasswordExpireService {

    private static final String PWD_UPDATE_TIME_PREFIX = "framework:password:update:";
    private static final long DAYS_BUFFER = 7;

    private final StringRedisTemplate redis;
    private final long expireDays; // 密码过期天数

    public PasswordExpireService(StringRedisTemplate redis, long expireDays) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.expireDays = expireDays;
    }

    /**
     * 检查密码是否过期
     *
     * @param userId 用户ID
     * @throws BusinessException 密码已过期时抛出
     */
    public void checkPasswordExpired(Long userId) {
        if (expireDays <= 0) {
            return; // 未启用密码过期策略
        }
        String key = buildPasswordUpdateKey(userId);
        Long updateTime = readPasswordUpdateTime(key, userId);

        if (updateTime == null) {
            // 没有记录修改时间，可能是新用户或老用户，初始化为当前时间
            recordPasswordChange(userId);
            return;
        }

        long now = System.currentTimeMillis();
        long expireMillis = TimeUnit.DAYS.toMillis(expireDays);
        if (now - updateTime > expireMillis) {
            long daysExpired = TimeUnit.MILLISECONDS.toDays(now - updateTime);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "密码已过期 " + daysExpired + " 天，请修改密码");
        }
    }

    /**
     * 记录密码修改时间
     *
     * @param userId 用户ID
     */
    public void recordPasswordChange(Long userId) {
        if (expireDays <= 0) {
            return;
        }
        String key = buildPasswordUpdateKey(userId);
        recordPasswordChange(key, System.currentTimeMillis());
    }

    private void recordPasswordChange(String key, long updateTime) {
        // TTL 设为过期天数 + 7 天缓冲
        long ttl = TimeUnit.DAYS.toSeconds(expireDays + DAYS_BUFFER);
        redis.opsForValue().set(key, String.valueOf(updateTime), ttl, TimeUnit.SECONDS);
    }

    /**
     * 获取密码剩余有效天数
     *
     * @param userId 用户ID
     * @return 剩余天数（-1 表示未启用过期策略）
     */
    public long getRemainingDays(Long userId) {
        if (expireDays <= 0) {
            return -1;
        }
        String key = buildPasswordUpdateKey(userId);
        Long updateTime = readPasswordUpdateTime(key, userId);
        if (updateTime == null) {
            return expireDays;
        }
        long expireMillis = TimeUnit.DAYS.toMillis(expireDays);
        long remaining = expireMillis - (System.currentTimeMillis() - updateTime);
        return remaining > 0 ? TimeUnit.MILLISECONDS.toDays(remaining) : 0;
    }

    private Long readPasswordUpdateTime(String key, Long userId) {
        String updateTimeStr = redis.opsForValue().get(key);
        if (updateTimeStr == null) {
            return null;
        }
        try {
            return Long.parseLong(updateTimeStr);
        } catch (NumberFormatException ex) {
            long now = System.currentTimeMillis();
            log.warn("[密码过期] Redis 中密码修改时间格式非法，已重置 userId={}", userId);
            recordPasswordChange(key, now);
            return now;
        }
    }

    private String buildPasswordUpdateKey(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return PWD_UPDATE_TIME_PREFIX + userId;
    }
}
