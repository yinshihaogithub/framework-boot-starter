package com.framework.mq.deadletter;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * MQ 消息失败记录
 * 记录消费失败、死信、重试全生命周期
 */
@Data
public class MqFailedMessage implements Serializable {

    private Long id;

    /** 原始消息ID */
    private String messageId;

    /** 链路追踪ID */
    private String traceId;

    /** 父消息ID（用于消息链路追踪） */
    private String parentMessageId;

    /** 业务Key（幂等键） */
    private String businessKey;

    /** 消息类型 */
    private String messageType;

    /** 交换机 */
    private String exchange;

    /** 路由键 */
    private String routingKey;

    /** 队列名 */
    private String queueName;

    /** 消息体（JSON） */
    private String payload;

    /** 失败原因 */
    private String errorMessage;

    /** 失败堆栈 */
    private String errorStack;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 状态：PENDING / RETRYING / SUCCESS / EXHAUSTED / MANUAL */
    private String status;

    /** 下次重试时间 */
    private Date nextRetryTime;

    /** 来源：CONSUME_FAIL / DEAD_LETTER / TTL_EXPIRE */
    private String source;

    /** 租户ID */
    private String tenantId;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /** 操作人（手动重发时记录） */
    private String operator;

    /** 人工补偿备注 */
    private String compensateRemark;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RETRYING = "RETRYING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_EXHAUSTED = "EXHAUSTED";
    public static final String STATUS_MANUAL = "MANUAL";

    public static final String SOURCE_CONSUME_FAIL = "CONSUME_FAIL";
    public static final String SOURCE_DEAD_LETTER = "DEAD_LETTER";
    public static final String SOURCE_TTL_EXPIRE = "TTL_EXPIRE";

    public MqFailedMessage copy() {
        MqFailedMessage copy = new MqFailedMessage();
        copy.setId(id);
        copy.setMessageId(messageId);
        copy.setTraceId(traceId);
        copy.setParentMessageId(parentMessageId);
        copy.setBusinessKey(businessKey);
        copy.setMessageType(messageType);
        copy.setExchange(exchange);
        copy.setRoutingKey(routingKey);
        copy.setQueueName(queueName);
        copy.setPayload(payload);
        copy.setErrorMessage(errorMessage);
        copy.setErrorStack(errorStack);
        copy.setRetryCount(retryCount);
        copy.setMaxRetry(maxRetry);
        copy.setStatus(status);
        copy.setNextRetryTime(copyDate(nextRetryTime));
        copy.setSource(source);
        copy.setTenantId(tenantId);
        copy.setCreateTime(copyDate(createTime));
        copy.setUpdateTime(copyDate(updateTime));
        copy.setOperator(operator);
        copy.setCompensateRemark(compensateRemark);
        return copy;
    }

    private static Date copyDate(Date date) {
        return date == null ? null : new Date(date.getTime());
    }
}
