package com.framework.localmessage.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Local message record.
 */
@Data
@Accessors(chain = true)
public class LocalMessage {

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
    private LocalMessageStatus status = LocalMessageStatus.PENDING;
    private int retryCount;
    private int maxRetry;
    private LocalDateTime nextRetryTime;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
