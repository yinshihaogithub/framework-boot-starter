package com.framework.auth.service;

import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * 短信验证码服务
 * - 发送验证码（限频 60s）
 * - 校验验证码
 * - 验证码 5 分钟有效
 */
@Slf4j
public class SmsCodeService {

    private static final String CODE_PREFIX = "framework:sms:code:";
    private static final String RATE_LIMIT_PREFIX = "framework:sms:limit:";
    private static final String SMS_UNAVAILABLE_MESSAGE = "短信验证码服务暂不可用，请稍后重试";
    private static final int CODE_BOUND = 1_000_000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final long codeExpireSeconds;    // 验证码有效期（秒）
    private final long resendIntervalSeconds; // 重发间隔（秒）
    private final SmsSender smsSender;
    private final IntSupplier codeGenerator;

    public SmsCodeService(StringRedisTemplate redis, long codeExpireSeconds, long resendIntervalSeconds) {
        this(redis, codeExpireSeconds, resendIntervalSeconds, new LoggingSmsSender());
    }

    public SmsCodeService(StringRedisTemplate redis, long codeExpireSeconds,
                          long resendIntervalSeconds, SmsSender smsSender) {
        this(redis, codeExpireSeconds, resendIntervalSeconds, smsSender,
                () -> SECURE_RANDOM.nextInt(CODE_BOUND));
    }

    SmsCodeService(StringRedisTemplate redis, long codeExpireSeconds,
                   long resendIntervalSeconds, SmsSender smsSender, IntSupplier codeGenerator) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.codeExpireSeconds = codeExpireSeconds;
        this.resendIntervalSeconds = resendIntervalSeconds;
        this.smsSender = Objects.requireNonNull(smsSender, "smsSender must not be null");
        this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator must not be null");
    }

    /**
     * 发送验证码
     *
     * @param phone 手机号
     * @return 生成的验证码
     */
    public String sendCode(String phone) {
        // 限频检查
        String rateLimitKey = RATE_LIMIT_PREFIX + phone;
        String code;
        try {
            if (Boolean.TRUE.equals(redis.hasKey(rateLimitKey))) {
                Long ttl = redis.getExpire(rateLimitKey, TimeUnit.SECONDS);
                throw new BusinessException(ResultCode.BUSINESS_ERROR,
                        "验证码发送过于频繁，请 " + (ttl != null ? ttl : 0) + " 秒后再试");
            }

            // 生成 6 位验证码
            code = generateCode();

            // 存储验证码
            redis.opsForValue().set(CODE_PREFIX + phone, code, codeExpireSeconds, TimeUnit.SECONDS);
            // 设置限频
            redis.opsForValue().set(rateLimitKey, "1", resendIntervalSeconds, TimeUnit.SECONDS);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw smsUnavailable("发送验证码", phone, e);
        }

        smsSender.send(phone, code, codeExpireSeconds);

        return code;
    }

    private String generateCode() {
        return String.format("%06d", Math.floorMod(codeGenerator.getAsInt(), CODE_BOUND));
    }

    /**
     * 校验验证码
     *
     * @param phone 手机号
     * @param code  用户输入的验证码
     * @return true=校验通过
     */
    public boolean verifyCode(String phone, String code) {
        if (phone == null || code == null || code.isEmpty()) {
            return false;
        }
        try {
            String storedCode = redis.opsForValue().get(CODE_PREFIX + phone);
            if (storedCode == null) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "验证码已过期，请重新获取");
            }
            if (!storedCode.equals(code)) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "验证码错误");
            }
            // 校验成功后删除验证码（一次性）
            redis.delete(CODE_PREFIX + phone);
            return true;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw smsUnavailable("校验验证码", phone, e);
        }
    }

    /**
     * 检查是否已发送验证码（未过期）
     */
    public boolean isCodeSent(String phone) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(CODE_PREFIX + phone));
        } catch (RuntimeException e) {
            log.warn("[短信验证码] 查询验证码发送状态失败 phone={} error={}", phone, e.getMessage());
            return false;
        }
    }

    private BusinessException smsUnavailable(String action, String phone, RuntimeException e) {
        log.error("[短信验证码] {}失败 phone={} error={}", action, phone, e.getMessage());
        return new BusinessException(ResultCode.SERVICE_ERROR, SMS_UNAVAILABLE_MESSAGE);
    }
}
