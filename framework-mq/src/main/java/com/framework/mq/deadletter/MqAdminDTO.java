package com.framework.mq.deadletter;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;

/**
 * MQ 管理控制台 DTO
 */
public class MqAdminDTO {

    @Data
    public static class MqFailedMessageQuery {
        private String queueName;
        private String status;
        private String businessKey;
        private String messageType;
        private int pageNum = 1;
        private int pageSize = 20;
    }

    @Data
    @Accessors(chain = true)
    public static class MqFailedMessageVO {
        private Long id;
        private String messageId;
        private String traceId;
        private String parentMessageId;
        private String businessKey;
        private String messageType;
        private String exchange;
        private String routingKey;
        private String queueName;
        private String payload;
        private String errorMessage;
        private String errorStack;
        private Integer retryCount;
        private Integer maxRetry;
        private String status;
        private String source;
        private String tenantId;
        private Date nextRetryTime;
        private Date createTime;
        private Date updateTime;
        private String operator;
        private String compensateRemark;
    }

    @Data
    @Accessors(chain = true)
    public static class MqQueueInfo {
        private String queueName;
        private long messageCount;
        private long consumerCount;
        private String state;
    }

    @Data
    @Accessors(chain = true)
    public static class MqStats {
        /** 各状态消息数 */
        private long pendingCount;
        private long retryingCount;
        private long successCount;
        private long exhaustedCount;
        private long totalCount;
        /** 今日失败数 */
        private long todayFailedCount;
        /** 今日重试成功数 */
        private long todayRetrySuccessCount;
        /** 队列列表 */
        private List<MqQueueInfo> queues;
        /** MQ运行配置 */
        private MqRuntimeInfo runtime;
    }

    @Data
    @Accessors(chain = true)
    public static class MqRuntimeInfo {
        private boolean enabled;
        private String provider;
        private boolean deadLetterEnabled;
        private boolean retryAvailable;
        private String deadLetterQueue;
        private int maxRetry;
        private long retryFixedDelay;
        private String failedMessageTableName;
        private List<MqProviderStatus> providers;
    }

    @Data
    @Accessors(chain = true)
    public static class MqProviderStatus {
        private String provider;
        private boolean active;
        private boolean available;
    }

    @Data
    @Accessors(chain = true)
    public static class ManualRetryRequest {
        /** 失败消息ID列表 */
        private List<Long> ids;
        /** 操作人 */
        private String operator;
        /** 补偿备注 */
        private String remark;
    }

    @Data
    @Accessors(chain = true)
    public static class ManualRetryResult {
        private int total;
        private int success;
        private int failed;
        private List<String> failedMessages;
    }
}
