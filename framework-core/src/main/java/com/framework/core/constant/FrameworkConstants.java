package com.framework.core.constant;

/**
 * 框架通用常量
 */
public final class FrameworkConstants {

    private FrameworkConstants() {}

    /** UTF-8 编码 */
    public static final String UTF_8 = "UTF-8";

    /** 默认时区 */
    public static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /** Token 请求头 */
    public static final String AUTH_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 租户ID请求头 */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /** 用户ID请求头（网关解析后注入） */
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_NAME_HEADER = "X-User-Name";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final String USER_PERMISSIONS_HEADER = "X-User-Permissions";

    /** 链路追踪ID */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    /** Redis Key 前缀 */
    public static final String CACHE_PREFIX = "framework:cache:";
    public static final String LOCK_PREFIX = "framework:lock:";
    public static final String IDEMPOTENT_PREFIX = "framework:idempotent:";
    public static final String SESSION_PREFIX = "framework:session:";
    public static final String TOKEN_BLACKLIST_PREFIX = "framework:token:blacklist:";
    public static final String RATE_LIMIT_PREFIX = "framework:rate:";

    /** 默认分页 */
    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 500;
}
