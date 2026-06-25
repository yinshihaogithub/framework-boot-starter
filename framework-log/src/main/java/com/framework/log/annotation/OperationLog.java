package com.framework.log.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 模块 */
    String module();

    /** 操作描述 */
    String action();

    /** 操作类型 */
    LogType type() default LogType.OTHER;

    /** 是否记录请求参数 */
    boolean saveParam() default true;

    /** 是否记录返回结果 */
    boolean saveResult() default false;

    enum LogType {
        INSERT, UPDATE, DELETE, QUERY, EXPORT, IMPORT, LOGIN, LOGOUT, OTHER
    }
}
