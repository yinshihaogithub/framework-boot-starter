package com.framework.security.annotation;

import java.lang.annotation.*;

/**
 * 必须登录
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireLogin {
}
