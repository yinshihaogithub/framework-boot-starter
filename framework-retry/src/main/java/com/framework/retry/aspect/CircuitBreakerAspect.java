package com.framework.retry.aspect;

import com.framework.core.exception.BusinessException;
import com.framework.retry.annotation.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 熔断降级切面
 * 基于 Resilience4j 实现熔断
 */
@Slf4j
@Aspect
@Component
public class CircuitBreakerAspect {

    private final ConcurrentMap<String, io.github.resilience4j.circuitbreaker.CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry registry;

    public CircuitBreakerAspect(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @Around("@annotation(com.framework.retry.annotation.CircuitBreaker)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        CircuitBreaker annotation = method.getAnnotation(CircuitBreaker.class);
        if (annotation == null) {
            annotation = signature.getMethod().getAnnotation(CircuitBreaker.class);
        }
        String breakerName = validate(annotation);

        io.github.resilience4j.circuitbreaker.CircuitBreaker breaker = getOrCreateBreaker(breakerName, annotation);

        // 检查熔断状态
        if (breaker.getState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN) {
            log.warn("[熔断器开启] name={}, 请求被拒绝", breakerName);
            return handleFallback(joinPoint, method, annotation, new BusinessException("服务熔断中，请稍后重试"));
        }

        try {
            long start = System.currentTimeMillis();
            Object result = breaker.executeSupplier(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    if (e instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(e);
                }
            });

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > annotation.timeout()) {
                log.warn("[慢调用] method={}, elapsed={}ms, threshold={}ms",
                        method.getName(), elapsed, annotation.timeout());
            }

            return result;

        } catch (Exception e) {
            Throwable failure = unwrapSupplierFailure(e);
            log.warn("[熔断器调用失败] name={}, error={}", breakerName, getFailureMessage(failure));
            return handleFallback(joinPoint, method, annotation, failure);
        }
    }

    private io.github.resilience4j.circuitbreaker.CircuitBreaker getOrCreateBreaker(String breakerName,
                                                                                     CircuitBreaker annotation) {
        return breakers.computeIfAbsent(breakerName, name -> {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .failureRateThreshold((float) (annotation.failureRate() * 100))
                    .slowCallDurationThreshold(Duration.ofMillis(annotation.timeout()))
                    .slowCallRateThreshold(80.0f)
                    .slidingWindowSize(annotation.slidingWindowSize())
                    .minimumNumberOfCalls(Math.min(10, annotation.slidingWindowSize()))
                    .waitDurationInOpenState(Duration.ofSeconds(annotation.waitDurationInOpenState()))
                    .permittedNumberOfCallsInHalfOpenState(10)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .build();
            return registry.circuitBreaker(name, config);
        });
    }

    private String validate(CircuitBreaker annotation) {
        if (annotation.name() == null || annotation.name().isBlank()) {
            throw new IllegalArgumentException("@CircuitBreaker name must not be blank");
        }
        if (annotation.failureRate() <= 0 || annotation.failureRate() > 1) {
            throw new IllegalArgumentException("@CircuitBreaker failureRate must be greater than 0 and less than or equal to 1");
        }
        if (annotation.timeout() <= 0) {
            throw new IllegalArgumentException("@CircuitBreaker timeout must be greater than 0");
        }
        if (annotation.slidingWindowSize() <= 0) {
            throw new IllegalArgumentException("@CircuitBreaker slidingWindowSize must be greater than 0");
        }
        if (annotation.waitDurationInOpenState() <= 0) {
            throw new IllegalArgumentException("@CircuitBreaker waitDurationInOpenState must be greater than 0");
        }
        return annotation.name().trim();
    }

    private Object handleFallback(ProceedingJoinPoint joinPoint, Method method,
                                  CircuitBreaker annotation, Throwable e) {
        String fallbackName = annotation.fallback().trim();
        if (!StringUtils.hasText(fallbackName)) {
            throw new BusinessException("服务暂时不可用: " + getFailureMessage(e));
        }

        Method fallbackMethod = ReflectionUtils.findMethod(
                joinPoint.getTarget().getClass(), fallbackName, method.getParameterTypes());

        if (fallbackMethod == null) {
            throw new BusinessException("回调方法不存在: " + fallbackName);
        }

        try {
            log.info("[熔断降级] method={}, fallback={}", method.getName(), fallbackName);
            ReflectionUtils.makeAccessible(fallbackMethod);
            return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
        } catch (InvocationTargetException ex) {
            Throwable target = ex.getTargetException();
            throw new BusinessException("降级方法执行失败: " + getFailureMessage(target));
        } catch (Exception ex) {
            throw new BusinessException("降级方法执行失败: " + ex.getMessage());
        }
    }

    private Throwable unwrapSupplierFailure(Exception e) {
        if (e.getClass() == RuntimeException.class && e.getCause() != null) {
            return e.getCause();
        }
        return e;
    }

    private String getFailureMessage(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
