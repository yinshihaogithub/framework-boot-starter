package com.framework.retry.aspect;

import com.framework.core.exception.BusinessException;
import com.framework.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 重试切面
 * - 固定间隔重试
 * - 指数退避重试
 * - 异常类型过滤
 * - 重试耗尽回调
 */
@Slf4j
@Aspect
@Component
public class RetryAspect {

    @Around("@annotation(com.framework.retry.annotation.Retry)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Retry annotation = method.getAnnotation(Retry.class);

        int maxAttempts = annotation.maxAttempts();
        long initialInterval = annotation.initialInterval();
        double multiplier = annotation.multiplier();
        long maxInterval = annotation.maxInterval();

        Throwable lastException = null;

        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("[重试] method={}, 第 {} 次重试（共 {} 次）", method.getName(), attempt, maxAttempts);
                }
                return joinPoint.proceed();

            } catch (Throwable e) {
                lastException = e;

                // 首次执行或重试已达上限
                if (attempt >= maxAttempts) {
                    log.error("[重试耗尽] method={}, 共重试 {} 次, 全部失败", method.getName(), maxAttempts, e);
                    break;
                }

                // 检查是否应该重试此异常
                if (!shouldRetry(e, annotation)) {
                    log.warn("[重试跳过] method={}, 异常 {} 不在重试范围", method.getName(), e.getClass().getSimpleName());
                    break;
                }

                // 计算等待时间
                long waitMs = calculateWaitTime(annotation.strategy(), initialInterval, multiplier, maxInterval, attempt);
                log.warn("[重试等待] method={}, 等待 {}ms 后第 {} 次重试, 异常: {}",
                        method.getName(), waitMs, attempt + 1, e.getMessage());

                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
            }
        }

        // 重试耗尽，尝试回调
        if (!annotation.fallback().isEmpty()) {
            return invokeFallback(joinPoint, method, annotation.fallback(), lastException);
        }

        throw lastException;
    }

    /**
     * 判断异常是否应该重试
     */
    private boolean shouldRetry(Throwable e, Retry annotation) {
        // noRetryFor 优先
        Class<? extends Throwable>[] noRetryFor = annotation.noRetryFor();
        if (noRetryFor.length > 0) {
            for (Class<? extends Throwable> exType : noRetryFor) {
                if (exType.isInstance(e)) {
                    return false;
                }
            }
        }

        // retryFor 为空则所有异常都重试
        Class<? extends Throwable>[] retryFor = annotation.retryFor();
        if (retryFor.length == 0) {
            return true;
        }

        // 检查是否匹配 retryFor
        return Arrays.stream(retryFor).anyMatch(exType -> exType.isInstance(e));
    }

    /**
     * 计算等待时间
     */
    private long calculateWaitTime(Retry.RetryStrategy strategy, long initialInterval,
                                   double multiplier, long maxInterval, int attempt) {
        long waitMs;
        if (strategy == Retry.RetryStrategy.EXPONENTIAL) {
            // 指数退避: initial * (multiplier ^ attempt)
            waitMs = (long) (initialInterval * Math.pow(multiplier, attempt));
        } else {
            // 固定间隔
            waitMs = initialInterval;
        }
        return Math.min(waitMs, maxInterval);
    }

    /**
     * 调用回调方法
     */
    private Object invokeFallback(ProceedingJoinPoint joinPoint, Method method,
                                  String fallbackName, Throwable lastException) {
        Method fallbackMethod = ReflectionUtils.findMethod(
                joinPoint.getTarget().getClass(), fallbackName, method.getParameterTypes());

        if (fallbackMethod == null) {
            throw new BusinessException("回调方法不存在: " + fallbackName);
        }

        try {
            log.info("[重试回调] method={}, fallback={}", method.getName(), fallbackName);
            ReflectionUtils.makeAccessible(fallbackMethod);
            return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
        } catch (Exception e) {
            throw new BusinessException("回调方法执行失败: " + e.getMessage());
        }
    }
}
