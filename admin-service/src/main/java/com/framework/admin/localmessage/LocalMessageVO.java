package com.framework.admin.localmessage;

import com.framework.localmessage.model.LocalMessage;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class LocalMessageVO {

    private Long id;
    private String messageId;
    private String traceId;
    private String parentMessageId;
    private String topic;
    private String businessKey;
    private String tenantId;
    private String operator;
    private String source;
    private String payload;
    private String status;
    private int retryCount;
    private int maxRetry;
    private String nextRetryTime;
    private String errorMessage;
    private String createTime;
    private String updateTime;

    public static LocalMessageVO from(LocalMessage message) {
        if (message == null) {
            return null;
        }
        return new LocalMessageVO()
                .setId(message.getId())
                .setMessageId(message.getMessageId())
                .setTraceId(message.getTraceId())
                .setParentMessageId(message.getParentMessageId())
                .setTopic(message.getTopic())
                .setBusinessKey(message.getBusinessKey())
                .setTenantId(message.getTenantId())
                .setOperator(message.getOperator())
                .setSource(message.getSource())
                .setPayload(message.getPayload())
                .setStatus(message.getStatus() == null ? null : message.getStatus().name())
                .setRetryCount(message.getRetryCount())
                .setMaxRetry(message.getMaxRetry())
                .setNextRetryTime(format(message.getNextRetryTime()))
                .setErrorMessage(message.getErrorMessage())
                .setCreateTime(format(message.getCreateTime()))
                .setUpdateTime(format(message.getUpdateTime()));
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.toString().replace('T', ' ');
    }
}
