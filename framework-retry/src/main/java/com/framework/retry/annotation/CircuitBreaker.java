package com.framework.retry.annotation;

import java.lang.annotation.*;

/**
 * 熔断降级注解
 * 配合 Resilience4j CircuitBreaker 使用
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreaker {

    /**
     * 熔断器名称（对应 Resilience4j 配置名）
     */
    String name();

    /**
     * 熔断后的回调方法名
     */
    String fallback() default "";

    /**
     * 超时时间（毫秒），超时计为失败
     */
    long timeout() default 2000L;

    /**
     * 失败率阈值（0-1）
     */
    double failureRate() default 0.5;

    /**
     * 滑动窗口大小
     */
    int slidingWindowSize() default 100;

    /**
     * 熔断持续时间（秒）
     */
    int waitDurationInOpenState() default 30;
}
