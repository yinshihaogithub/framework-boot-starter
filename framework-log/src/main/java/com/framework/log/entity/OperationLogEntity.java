package com.framework.log.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 操作日志实体（对应 sys_operation_log 表）
 */
@Data
public class OperationLogEntity implements Serializable {

    private Long id;

    /** 日志类型：OPERATION / API / LOGIN / EXCEPTION */
    private String logType;

    /** 模块 */
    private String module;

    /** 操作描述 */
    private String action;

    /** 操作类型：INSERT/UPDATE/DELETE/QUERY/OTHER */
    private String operationType;

    /** 请求URI */
    private String uri;

    /** 请求方法 */
    private String httpMethod;

    /** 执行方法（类名.方法名） */
    private String method;

    /** 请求参数（JSON，已脱敏） */
    private String params;

    /** 返回结果（JSON，已脱敏） */
    private String result;

    /** 是否成功 */
    private Boolean success;

    /** 错误信息 */
    private String errorMessage;

    /** 耗时（毫秒） */
    private Long elapsedMs;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 操作IP */
    private String clientIp;

    /** 链路追踪ID */
    private String traceId;

    /** 创建时间 */
    private Date createTime;
}
