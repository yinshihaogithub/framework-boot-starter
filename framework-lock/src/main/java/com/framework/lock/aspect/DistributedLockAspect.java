package com.framework.lock.aspect;

import com.framework.lock.annotation.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁切面
 * - 解析 SpEL key
 * - 获取/释放锁
 * - 看门狗自动续期（leaseTime = -1）
 * - 获取失败回调 fallback
 */
@Slf4j
@Aspect
@Component
public class DistributedLockAspect {

    private static final String LOCK_KEY_PREFIX = "framework:lock:";
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParserContext TEMPLATE_PARSER_CONTEXT = new TemplateParserContext();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final RedissonClient redissonClient;

    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(com.framework.lock.annotation.DistributedLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        DistributedLock annotation = method.getAnnotation(DistributedLock.class);
        validate(annotation, method);

        // 解析 SpEL key
        String lockKey = LOCK_KEY_PREFIX + resolveKey(annotation.key(), signature, method, joinPoint.getArgs());
        long waitTime = annotation.waitTime();
        long leaseTime = annotation.leaseTime();
        TimeUnit unit = annotation.unit();

        RLock lock = getLock(lockKey, annotation);
        boolean locked = false;

        try {
            try {
                // leaseTime = -1 时启用看门狗自动续期
                locked = leaseTime == -1
                        ? lock.tryLock(waitTime, unit)
                        : lock.tryLock(waitTime, leaseTime, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[分布式锁] 获取锁被中断 key={}", lockKey, e);
                return handleFallback(joinPoint, method, annotation);
            } catch (Exception e) {
                log.warn("[分布式锁] 获取锁异常 key={} error={}", lockKey, e.getMessage());
                return handleFallback(joinPoint, method, annotation);
            }

            if (!locked) {
                log.warn("[分布式锁] 获取锁失败 key={}", lockKey);
                return handleFallback(joinPoint, method, annotation);
            }

            log.debug("[分布式锁] 获取锁成功 key={}", lockKey);
            return joinPoint.proceed();
        } finally {
            if (locked) {
                releaseLock(lock, lockKey);
            }
        }
    }

    private RLock getLock(String lockKey, DistributedLock annotation) {
        try {
            return redissonClient.getLock(lockKey);
        } catch (Exception e) {
            log.warn("[分布式锁] 创建锁对象异常 key={} error={}", lockKey, e.getMessage());
            throw new com.framework.core.exception.BusinessException(
                    com.framework.core.result.ResultCode.LOCK_FAIL, annotation.message());
        }
    }

    private void releaseLock(RLock lock, String lockKey) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[分布式锁] 释放锁 key={}", lockKey);
            }
        } catch (Exception e) {
            log.warn("[分布式锁] 释放锁异常 key={} error={}", lockKey, e.getMessage());
        }
    }

    private void validate(DistributedLock annotation, Method method) {
        if (annotation.waitTime() < 0) {
            throw new IllegalArgumentException("@DistributedLock waitTime must be greater than or equal to 0: " + method);
        }
        if (annotation.leaseTime() != -1 && annotation.leaseTime() <= 0) {
            throw new IllegalArgumentException("@DistributedLock leaseTime must be -1 or greater than 0: " + method);
        }
        if (annotation.unit() == null) {
            throw new IllegalArgumentException("@DistributedLock unit must not be null: " + method);
        }
    }

    /**
     * 解析 SpEL 表达式
     * 支持 #{#param.field} 和 #{#param} 格式
     */
    private String resolveKey(String keyExpression, MethodSignature signature, Method method, Object[] args) {
        if (!StringUtils.hasText(keyExpression)) {
            throw new IllegalArgumentException("@DistributedLock key must not be blank: " + method);
        }
        if (!keyExpression.contains("#")) {
            return keyExpression.trim();
        }

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("args", args);
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }
        registerParameterNames(context, signature, method, args);
        try {
            Object value = keyExpression.contains("#{")
                    ? PARSER.parseExpression(keyExpression, TEMPLATE_PARSER_CONTEXT).getValue(context)
                    : PARSER.parseExpression(keyExpression).getValue(context);
            String resolved = value != null ? value.toString() : null;
            if (!StringUtils.hasText(resolved)) {
                throw new IllegalArgumentException("@DistributedLock key must not resolve to blank: " + method);
            }
            return resolved.trim();
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("@DistributedLock key SpEL parse failed: " + keyExpression, e);
        }
    }

    private void registerParameterNames(StandardEvaluationContext context, MethodSignature signature,
                                        Method method, Object[] args) {
        String[] paramNames = signature.getParameterNames();
        if (paramNames == null || paramNames.length == 0) {
            paramNames = NAME_DISCOVERER.getParameterNames(method);
        }
        if (paramNames == null) {
            return;
        }
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
    }

    /**
     * 获取锁失败回调
     */
    private Object handleFallback(ProceedingJoinPoint joinPoint, Method method, DistributedLock annotation) {
        String fallbackName = annotation.fallback();
        if (fallbackName.isEmpty()) {
            throw new com.framework.core.exception.BusinessException(
                    com.framework.core.result.ResultCode.LOCK_FAIL, annotation.message());
        }

        // 反射调用回调方法
        Method fallbackMethod = ReflectionUtils.findMethod(
                joinPoint.getTarget().getClass(), fallbackName, method.getParameterTypes());
        if (fallbackMethod == null) {
            throw new com.framework.core.exception.BusinessException(
                    com.framework.core.result.ResultCode.LOCK_FAIL,
                    "回调方法不存在: " + fallbackName);
        }

        try {
            ReflectionUtils.makeAccessible(fallbackMethod);
            return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
        } catch (Exception e) {
            throw new com.framework.core.exception.BusinessException(
                    com.framework.core.result.ResultCode.LOCK_FAIL, "回调方法执行失败: " + e.getMessage());
        }
    }
}
