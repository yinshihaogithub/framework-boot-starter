package com.framework.mq.core;

import com.framework.core.trace.TraceContext;
import lombok.Data;
import java.io.Serializable;
import java.util.UUID;

/**
 * 消息包装器
 * 包装业务消息，附加消息ID、时间戳、重试次数等元数据
 */
@Data
public class MessageWrapper<T> implements Serializable {

    /** 消息唯一ID（用于幂等消费） */
    private String messageId;

    /** 链路追踪ID */
    private String traceId;

    /** 父消息ID（用于消息链路追踪） */
    private String parentMessageId;

    /** 业务Key（如订单号，用于幂等判断） */
    private String businessKey;

    /** 消息类型 */
    private String type;

    /** 消息体 */
    private T payload;

    /** 创建时间戳 */
    private long timestamp;

    /** 来源服务 */
    private String source;

    public MessageWrapper() {
        this.messageId = UUID.randomUUID().toString().replace("-", "");
        this.traceId = TraceContext.getTraceId();
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> MessageWrapper<T> of(T payload) {
        return of(null, payload);
    }

    public static <T> MessageWrapper<T> of(String businessKey, T payload) {
        MessageWrapper<T> wrapper = new MessageWrapper<>();
        wrapper.setBusinessKey(businessKey);
        wrapper.setPayload(payload);
        wrapper.setType(payload.getClass().getSimpleName());
        return wrapper;
    }

    public static <T> MessageWrapper<T> of(String businessKey, String type, T payload) {
        MessageWrapper<T> wrapper = new MessageWrapper<>();
        wrapper.setBusinessKey(businessKey);
        wrapper.setType(type);
        wrapper.setPayload(payload);
        return wrapper;
    }
}
