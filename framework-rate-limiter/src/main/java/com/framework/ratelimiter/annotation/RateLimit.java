package com.framework.ratelimiter.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流 key，支持 SpEL
     */
    String key() default "";

    /**
     * 时间窗口内允许的请求数
     */
    int limit() default 100;

    /**
     * 时间窗口大小
     */
    int window() default 60;

    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 限流类型
     */
    LimitType limitType() default LimitType.GLOBAL;

    /**
     * 被限流时的提示信息
     */
    String message() default "请求过于频繁，请稍后再试";

    enum LimitType {
        /** 全局 */
        GLOBAL,
        /** 按 IP */
        IP,
        /** 按用户 */
        USER,
        /** 默认（按 key） */
        DEFAULT
    }
}
