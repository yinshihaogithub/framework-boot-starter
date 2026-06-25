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
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

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
        Method method = signature.getMethod();
        CircuitBreaker annotation = method.getAnnotation(CircuitBreaker.class);

        io.github.resilience4j.circuitbreaker.CircuitBreaker breaker = getOrCreateBreaker(annotation);

        // 检查熔断状态
        if (breaker.getState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN) {
            log.warn("[熔断器开启] name={}, 请求被拒绝", annotation.name());
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
            log.warn("[熔断器调用失败] name={}, error={}", annotation.name(), e.getMessage());
            return handleFallback(joinPoint, method, annotation, e);
        }
    }

    private io.github.resilience4j.circuitbreaker.CircuitBreaker getOrCreateBreaker(CircuitBreaker annotation) {
        return breakers.computeIfAbsent(annotation.name(), name -> {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .failureRateThreshold((float) annotation.failureRate())
                    .slowCallDurationThreshold(Duration.ofMillis(annotation.timeout()))
                    .slowCallRateThreshold(80.0f)
                    .slidingWindowSize(annotation.slidingWindowSize())
                    .minimumNumberOfCalls(10)
                    .waitDurationInOpenState(Duration.ofSeconds(annotation.waitDurationInOpenState()))
                    .permittedNumberOfCallsInHalfOpenState(10)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .build();
            return registry.circuitBreaker(name, config);
        });
    }

    private Object handleFallback(ProceedingJoinPoint joinPoint, Method method,
                                  CircuitBreaker annotation, Exception e) {
        if (annotation.fallback().isEmpty()) {
            throw new BusinessException("服务暂时不可用: " + e.getMessage());
        }

        Method fallbackMethod = ReflectionUtils.findMethod(
                joinPoint.getTarget().getClass(), annotation.fallback(), method.getParameterTypes());

        if (fallbackMethod == null) {
            throw new BusinessException("回调方法不存在: " + annotation.fallback());
        }

        try {
            log.info("[熔断降级] method={}, fallback={}", method.getName(), annotation.fallback());
            ReflectionUtils.makeAccessible(fallbackMethod);
            return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
        } catch (Exception ex) {
            throw new BusinessException("降级方法执行失败: " + ex.getMessage());
        }
    }
}
