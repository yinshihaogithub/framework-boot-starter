package com.framework.lock.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解
 * 基于 Redisson 实现，支持 SpEL 表达式 key
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁的 key，支持 SpEL
     * 例: "order:#{#orderId}"
     */
    String key();

    /**
     * 等待获取锁的最大时间（秒）
     */
    long waitTime() default 3;

    /**
     * 持有锁的最大时间（秒）
     * 设为 -1 时启用看门狗自动续期
     */
    long leaseTime() default -1;

    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 获取锁失败时的提示信息
     */
    String message() default "操作繁忙，请稍后再试";

    /**
     * 获取锁失败时的回调方法名
     */
    String fallback() default "";
}
