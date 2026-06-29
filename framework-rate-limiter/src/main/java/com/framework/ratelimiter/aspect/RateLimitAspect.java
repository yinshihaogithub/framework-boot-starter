package com.framework.ratelimiter.aspect;

import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import com.framework.ratelimiter.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * 基于 Redisson RRateLimiter（滑动窗口）
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private static final String RATE_KEY_PREFIX = "framework:rate:";
    private static final ParserContext TEMPLATE_PARSER_CONTEXT = new TemplateParserContext();

    private final RedissonClient redissonClient;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public RateLimitAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(com.framework.ratelimiter.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        validate(annotation, method);

        String rateLimitKey = buildKey(annotation, method, signature, joinPoint.getArgs());

        // Redisson 异常时按限流失败处理，避免底层基础设施异常穿透到业务接口。
        boolean acquired = acquire(rateLimitKey, annotation);

        if (!acquired) {
            log.warn("[限流拦截] key={}", rateLimitKey);
            throw new BusinessException(ResultCode.RATE_LIMITED, annotation.message());
        }

        return joinPoint.proceed();
    }

    private boolean acquire(String rateLimitKey, RateLimit annotation) {
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(rateLimitKey);
            // 初始化限流器（OVERALL = 单实例总速率；PER_CLIENT = 每实例独立）
            rateLimiter.trySetRate(RateType.OVERALL, annotation.limit(), annotation.window(),
                    toRateIntervalUnit(annotation.unit()));

            // 尝试获取令牌
            return rateLimiter.tryAcquire();
        } catch (Exception e) {
            log.warn("[限流服务异常] key={}, error={}", rateLimitKey, e.getMessage());
            throw new BusinessException(ResultCode.RATE_LIMITED, "限流服务暂不可用，请稍后重试");
        }
    }

    private void validate(RateLimit annotation, Method method) {
        if (annotation.limit() <= 0) {
            throw new IllegalArgumentException("@RateLimit limit must be greater than 0: " + method);
        }
        if (annotation.window() <= 0) {
            throw new IllegalArgumentException("@RateLimit window must be greater than 0: " + method);
        }
        if (annotation.unit() != TimeUnit.SECONDS
                && annotation.unit() != TimeUnit.MINUTES
                && annotation.unit() != TimeUnit.HOURS) {
            throw new IllegalArgumentException("@RateLimit unit only supports SECONDS, MINUTES or HOURS: " + method);
        }
    }

    private String buildKey(RateLimit annotation, Method method, MethodSignature signature, Object[] args) {
        String key = resolveKey(annotation.key(), method, signature, args);
        String keyPart = StringUtils.hasText(key)
                ? key
                : method.getDeclaringClass().getSimpleName() + ":" + method.getName();

        return switch (annotation.limitType()) {
            case GLOBAL -> RATE_KEY_PREFIX + "global:" + keyPart;
            case IP -> RATE_KEY_PREFIX + "ip:" + getClientIp() + ":" + keyPart;
            case USER -> RATE_KEY_PREFIX + "user:" + getUserId() + ":" + keyPart;
            case DEFAULT -> RATE_KEY_PREFIX + "default:" + keyPart;
        };
    }

    private String resolveKey(String configuredKey, Method method, MethodSignature signature, Object[] args) {
        if (!StringUtils.hasText(configuredKey)) {
            return "";
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("args", args);
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }
        String[] parameterNames = signature.getParameterNames();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        Object value;
        try {
            value = configuredKey.contains("#{")
                    ? expressionParser.parseExpression(configuredKey, TEMPLATE_PARSER_CONTEXT).getValue(context)
                    : expressionParser.parseExpression("'" + configuredKey.replace("'", "''") + "'").getValue(context);
        } catch (Exception e) {
            throw new IllegalArgumentException("@RateLimit key SpEL parse failed: " + configuredKey, e);
        }
        String resolved = value != null ? String.valueOf(value) : null;
        if (!StringUtils.hasText(resolved)) {
            throw new IllegalArgumentException("@RateLimit key must not resolve to blank: " + method);
        }
        return resolved.trim();
    }

    private RateIntervalUnit toRateIntervalUnit(TimeUnit unit) {
        return switch (unit) {
            case SECONDS -> RateIntervalUnit.SECONDS;
            case MINUTES -> RateIntervalUnit.MINUTES;
            case HOURS -> RateIntervalUnit.HOURS;
            default -> throw new IllegalArgumentException("@RateLimit unit only supports SECONDS, MINUTES or HOURS");
        };
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }

    private String getUserId() {
        try {
            Class<?> clazz = Class.forName("com.framework.auth.context.UserContextHolder");
            Object userId = clazz.getMethod("getUserId").invoke(null);
            return userId != null ? userId.toString() : "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
