package com.framework.idempotent.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等 key，支持 SpEL
     * 为空时按策略自动生成
     */
    String key() default "";

    /**
     * 幂等窗口（秒）
     */
    int expire() default 10;

    /**
     * 幂等策略
     */
    IdempotentStrategy strategy() default IdempotentStrategy.REQUEST_HASH;

    /**
     * 被拦截时的提示信息
     */
    String message() default "请勿重复提交";

    enum IdempotentStrategy {
        /** Token 策略：前端先获取 token，提交时校验并删除 */
        TOKEN,
        /** 请求体 hash，窗口内相同请求拦截 */
        REQUEST_HASH,
        /** 业务键（如订单号），由 SpEL 指定 */
        BUSINESS_KEY
    }
}
