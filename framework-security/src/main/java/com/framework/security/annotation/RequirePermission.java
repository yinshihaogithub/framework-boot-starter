package com.framework.security.annotation;

import java.lang.annotation.*;

/**
 * 必须指定权限
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /** 权限标识，满足任一即可 */
    String[] value();

    /** 是否需要全部满足（AND），默认 false（OR） */
    boolean logicalAnd() default false;
}
