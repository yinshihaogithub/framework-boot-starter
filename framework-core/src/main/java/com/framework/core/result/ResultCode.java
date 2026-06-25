package com.framework.core.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务码规范：
 * 200       成功
 * 400-499   客户端错误
 * 500-599   服务端错误
 * 1000-1999 通用业务错误
 * 2000-2999 鉴权/权限错误
 * 3000-3999 各业务模块自定义
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),

    FAIL(500, "系统繁忙，请稍后重试"),
    SERVICE_ERROR(501, "服务异常"),

    PARAM_ERROR(1001, "参数校验失败"),
    BUSINESS_ERROR(1002, "业务处理失败"),
    REPEAT_SUBMIT(1003, "请勿重复提交"),

    TOKEN_INVALID(2001, "Token无效"),
    TOKEN_EXPIRED(2002, "Token已过期"),
    LOGIN_FAIL(2003, "账号或密码错误"),
    ACCOUNT_LOCKED(2004, "账号已被锁定"),
    PERMISSION_DENIED(2005, "权限不足"),

    RATE_LIMITED(3001, "请求过于频繁，请稍后再试"),
    LOCK_FAIL(3002, "操作繁忙，请稍后再试"),
    IDEMPOTENT_FAIL(3003, "重复请求已被拦截");

    private final int code;
    private final String message;
}
