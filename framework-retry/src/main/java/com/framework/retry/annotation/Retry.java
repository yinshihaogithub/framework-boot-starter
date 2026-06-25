package com.framework.retry.annotation;

import java.lang.annotation.*;

/**
 * 重试注解
 * 支持固定间隔和指数退避两种策略
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retry {

    /**
     * 最大重试次数（不含首次执行）
     */
    int maxAttempts() default 3;

    /**
     * 重试策略
     */
    RetryStrategy strategy() default RetryStrategy.FIXED;

    /**
     * 初始等待间隔（毫秒）
     * FIXED: 每次固定等待此值
     * EXPONENTIAL: 首次等待此值，后续按 multiplier 递增
     */
    long initialInterval() default 1000;

    /**
     * 指数退避乘数（仅 EXPONENTIAL 生效）
     * actualInterval = initialInterval * (multiplier ^ attempt)
     */
    double multiplier() default 2.0;

    /**
     * 最大等待间隔（毫秒），防止指数退避过长
     */
    long maxInterval() default 30000;

    /**
     * 需要重试的异常类型（为空则所有异常都重试）
     */
    Class<? extends Throwable>[] retryFor() default {};

    /**
     * 不需要重试的异常类型（优先于 retryFor）
     */
    Class<? extends Throwable>[] noRetryFor() default {};

    /**
     * 重试耗尽后的回调方法名
     * 回调方法签名需与原方法一致
     */
    String fallback() default "";

    enum RetryStrategy {
        /** 固定间隔 */
        FIXED,
        /** 指数退避 */
        EXPONENTIAL
    }
}
