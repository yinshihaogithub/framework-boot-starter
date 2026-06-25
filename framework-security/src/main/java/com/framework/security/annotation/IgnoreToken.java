package com.framework.security.annotation;

import java.lang.annotation.*;

/**
 * 跳过鉴权（公开接口）
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreToken {
}
