package com.framework.auth.service;

import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 密码过期策略服务
 * - 检查密码是否过期
 * - 记录密码修改时间
 */
@Slf4j
public class PasswordExpireService {

    private static final String PWD_UPDATE_TIME_PREFIX = "framework:password:update:";

    private final StringRedisTemplate redis;
    private final long expireDays; // 密码过期天数

    public PasswordExpireService(StringRedisTemplate redis, long expireDays) {
        this.redis = redis;
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
        String key = PWD_UPDATE_TIME_PREFIX + userId;
        String updateTimeStr = redis.opsForValue().get(key);

        if (updateTimeStr == null) {
            // 没有记录修改时间，可能是新用户或老用户，初始化为当前时间
            recordPasswordChange(userId);
            return;
        }

        long updateTime = Long.parseLong(updateTimeStr);
        long expireMillis = expireDays * 24 * 60 * 60 * 1000L;
        if (System.currentTimeMillis() - updateTime > expireMillis) {
            long daysExpired = (System.currentTimeMillis() - updateTime) / (24 * 60 * 60 * 1000);
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
        String key = PWD_UPDATE_TIME_PREFIX + userId;
        // TTL 设为过期天数 + 7 天缓冲
        long ttl = (expireDays + 7) * 24 * 60 * 60;
        redis.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), ttl, TimeUnit.SECONDS);
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
        String key = PWD_UPDATE_TIME_PREFIX + userId;
        String updateTimeStr = redis.opsForValue().get(key);
        if (updateTimeStr == null) {
            return expireDays;
        }
        long updateTime = Long.parseLong(updateTimeStr);
        long expireMillis = expireDays * 24 * 60 * 60 * 1000L;
        long remaining = expireMillis - (System.currentTimeMillis() - updateTime);
        return remaining > 0 ? remaining / (24 * 60 * 60 * 1000) : 0;
    }
}
