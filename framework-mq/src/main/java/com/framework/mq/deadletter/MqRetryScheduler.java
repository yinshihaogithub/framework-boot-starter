package com.framework.mq.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.framework.mq.core.MessageWrapper;
import com.framework.mq.producer.MqMessageSenderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * MQ 失败消息重试调度器
 * 定时扫描 PENDING 状态的失败消息，到达重试时间后自动重发
 *
 * 重试策略：指数退避
 *   第1次：1分钟后
 *   第2次：5分钟后
 *   第3次：30分钟后
 *   超过3次 → 标记 EXHAUSTED，等待人工处理
 */
@Slf4j
public class MqRetryScheduler {

    private static final String LEGACY_MESSAGE_TYPE = "LegacyMessage";

    private final DeadLetterHandler deadLetterHandler;
    private final MqMessageSenderRegistry senderRegistry;
    private final int maxRetry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MqRetryScheduler(DeadLetterHandler deadLetterHandler,
                            MqMessageSenderRegistry senderRegistry,
                            int maxRetry) {
        this.deadLetterHandler = deadLetterHandler;
        this.senderRegistry = senderRegistry;
        this.maxRetry = maxRetry;
    }

    /**
     * 每30秒扫描一次待重试消息
     */
    @Scheduled(fixedDelayString = "${framework.mq.retry.fixed-delay:30000}")
    public void scanAndRetry() {
        var store = deadLetterHandler.getFailedMessageStore();
        if (store.isEmpty()) {
            return;
        }

        Date now = new Date();
        List<MqFailedMessage> toRetry = new ArrayList<>();

        for (MqFailedMessage msg : store.values()) {
            if (!MqFailedMessage.STATUS_PENDING.equals(msg.getStatus())) {
                continue;
            }
            if (msg.getNextRetryTime() == null || !msg.getNextRetryTime().after(now)) {
                toRetry.add(msg);
            }
        }

        if (toRetry.isEmpty()) {
            return;
        }

        log.info("[重试调度] 发现 {} 条待重试消息", toRetry.size());

        for (MqFailedMessage msg : toRetry) {
            retryMessage(msg);
        }
    }

    /**
     * 重试单条消息
     * 注意：发送成功仅表示消息已投递到 MQ，不代表消费成功
     * 此处标记为 RETRYING 状态，等待消费端确认或下一次失败
     */
    private void retryMessage(MqFailedMessage msg) {
        try {
            msg.setStatus(MqFailedMessage.STATUS_RETRYING);
            msg.setUpdateTime(new Date());

            // 重发到原交换机，保留原始消息链路元数据。
            senderRegistry.activeSender().send(msg.getExchange(), msg.getRoutingKey(), restoreWrapper(msg));

            int newRetryCount = msg.getRetryCount() + 1;
            msg.setRetryCount(newRetryCount);

            if (newRetryCount >= maxRetry) {
                // 已达最大重试次数，标记为耗尽（等待人工处理）
                msg.setStatus(MqFailedMessage.STATUS_EXHAUSTED);
                log.warn("[重试耗尽] id={}, messageId={}, 已重试{}次", msg.getId(), msg.getMessageId(), newRetryCount);
            } else {
                // 发送成功但未达最大次数，保持 PENDING 等待消费端确认
                // 如果消费端再次失败，会通过死信机制回到失败记录
                msg.setStatus(MqFailedMessage.STATUS_PENDING);
                msg.setNextRetryTime(calculateNextRetryTime(newRetryCount));
                log.info("[重试发送] id={}, messageId={}, 第{}次重试已发送，等待消费确认", msg.getId(), msg.getMessageId(), newRetryCount);
            }

            msg.setUpdateTime(new Date());
            deadLetterHandler.updateRecord(msg);

        } catch (Exception e) {
            log.error("[重试失败] id={}, messageId={}, error={}", msg.getId(), msg.getMessageId(), e.getMessage());

            int newRetryCount = msg.getRetryCount() + 1;
            msg.setRetryCount(newRetryCount);

            if (newRetryCount >= maxRetry) {
                msg.setStatus(MqFailedMessage.STATUS_EXHAUSTED);
                msg.setErrorMessage(msg.getErrorMessage() + " | 重试失败: " + e.getMessage());
            } else {
                msg.setStatus(MqFailedMessage.STATUS_PENDING);
                msg.setNextRetryTime(calculateNextRetryTime(newRetryCount));
            }
            msg.setUpdateTime(new Date());
            deadLetterHandler.updateRecord(msg);
        }
    }

    /**
     * 计算下次重试时间（指数退避：1min, 5min, 30min, 2h）
     */
    private Date calculateNextRetryTime(int retryCount) {
        long[] intervals = {60_000L, 300_000L, 1_800_000L, 7_200_000L};
        long interval = retryCount < intervals.length
                ? intervals[retryCount] : intervals[intervals.length - 1];
        return new Date(System.currentTimeMillis() + interval);
    }

    /**
     * 手动重发（管理后台调用）
     */
    public boolean manualRetry(Long id, String operator) {
        return manualRetry(id, operator, null);
    }

    /**
     * 手动重发（管理后台调用）
     */
    public boolean manualRetry(Long id, String operator, String remark) {
        MqFailedMessage msg = deadLetterHandler.getById(id);
        if (msg == null) {
            return false;
        }
        String normalizedOperator = normalize(operator);
        String normalizedRemark = normalize(remark);

        try {
            senderRegistry.activeSender().send(msg.getExchange(), msg.getRoutingKey(), restoreWrapper(msg));
            msg.setStatus(MqFailedMessage.STATUS_MANUAL);
            msg.setOperator(normalizedOperator);
            msg.setCompensateRemark(normalizedRemark);
            msg.setRetryCount(retryCount(msg) + 1);
            msg.setNextRetryTime(null);
            msg.setUpdateTime(new Date());
            deadLetterHandler.updateRecord(msg);
            log.info("[手动重发] id={}, messageId={}, traceId={}, operator={}",
                    id, msg.getMessageId(), msg.getTraceId(), normalizedOperator);
            return true;
        } catch (Exception e) {
            log.error("[手动重发失败] id={}, error={}", id, e.getMessage());
            recordManualRetryFailure(msg, normalizedOperator, normalizedRemark, e);
            return false;
        }
    }

    private void recordManualRetryFailure(MqFailedMessage msg, String operator, String remark, Exception exception) {
        int newRetryCount = retryCount(msg) + 1;
        msg.setRetryCount(newRetryCount);
        msg.setOperator(operator);
        msg.setCompensateRemark(remark == null ? "手动重发失败" : remark);
        msg.setErrorMessage(appendErrorMessage(msg.getErrorMessage(), "手动重发失败: " + exception.getMessage()));
        if (newRetryCount >= retryLimit(msg)) {
            msg.setStatus(MqFailedMessage.STATUS_EXHAUSTED);
            msg.setNextRetryTime(null);
        } else {
            msg.setStatus(MqFailedMessage.STATUS_PENDING);
            msg.setNextRetryTime(calculateNextRetryTime(newRetryCount));
        }
        msg.setUpdateTime(new Date());
        deadLetterHandler.updateRecord(msg);
    }

    /**
     * 批量手动重发
     */
    public MqAdminDTO.ManualRetryResult batchManualRetry(List<Long> ids, String operator) {
        return batchManualRetry(ids, operator, null);
    }

    /**
     * 批量手动重发
     */
    public MqAdminDTO.ManualRetryResult batchManualRetry(List<Long> ids, String operator, String remark) {
        if (ids == null) {
            throw new IllegalArgumentException("ids must not be null");
        }
        MqAdminDTO.ManualRetryResult result = new MqAdminDTO.ManualRetryResult();
        result.setTotal(ids.size());
        List<String> failedMessages = new ArrayList<>();

        int success = 0;
        int failed = 0;

        for (Long id : ids) {
            if (manualRetry(id, operator, remark)) {
                success++;
            } else {
                failed++;
                failedMessages.add("ID=" + id + " 重发失败");
            }
        }

        result.setSuccess(success);
        result.setFailed(failed);
        result.setFailedMessages(failedMessages);
        return result;
    }

    @SuppressWarnings("unchecked")
    private MessageWrapper<Object> restoreWrapper(MqFailedMessage msg) {
        MessageWrapper<Object> wrapper = null;
        boolean legacyPayload = false;
        try {
            JsonNode payloadNode = objectMapper.readTree(msg.getPayload());
            if (isMessageWrapperNode(payloadNode)) {
                wrapper = objectMapper.treeToValue(payloadNode, MessageWrapper.class);
            } else {
                legacyPayload = true;
            }
        } catch (Exception ignored) {
            // Legacy failed records may store only the raw business payload.
            legacyPayload = true;
        }
        if (wrapper == null) {
            wrapper = MessageWrapper.of(msg.getBusinessKey(), messageType(msg), msg.getPayload());
        }
        if (legacyPayload) {
            fillRecordMetadata(wrapper, msg);
        } else {
            fillMissingMetadata(wrapper, msg);
        }
        return wrapper;
    }

    private boolean isMessageWrapperNode(JsonNode node) {
        return node != null
                && node.isObject()
                && (node.has("payload")
                || node.has("messageId")
                || node.has("traceId")
                || node.has("businessKey")
                || node.has("type"));
    }

    private void fillMissingMetadata(MessageWrapper<?> wrapper, MqFailedMessage msg) {
        if (isBlank(wrapper.getMessageId())) {
            wrapper.setMessageId(msg.getMessageId());
        }
        if (isBlank(wrapper.getTraceId())) {
            wrapper.setTraceId(msg.getTraceId());
        }
        if (isBlank(wrapper.getParentMessageId())) {
            wrapper.setParentMessageId(msg.getParentMessageId());
        }
        if (isBlank(wrapper.getBusinessKey())) {
            wrapper.setBusinessKey(msg.getBusinessKey());
        }
        if (isBlank(wrapper.getType())) {
            wrapper.setType(messageType(msg));
        }
    }

    private void fillRecordMetadata(MessageWrapper<?> wrapper, MqFailedMessage msg) {
        wrapper.setMessageId(msg.getMessageId());
        wrapper.setTraceId(msg.getTraceId());
        wrapper.setParentMessageId(msg.getParentMessageId());
        wrapper.setBusinessKey(msg.getBusinessKey());
        wrapper.setType(messageType(msg));
    }

    private String messageType(MqFailedMessage msg) {
        return isBlank(msg.getMessageType()) ? LEGACY_MESSAGE_TYPE : msg.getMessageType();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int retryCount(MqFailedMessage msg) {
        return msg.getRetryCount() == null ? 0 : msg.getRetryCount();
    }

    private int retryLimit(MqFailedMessage msg) {
        return msg.getMaxRetry() == null || msg.getMaxRetry() <= 0 ? maxRetry : msg.getMaxRetry();
    }

    private String appendErrorMessage(String current, String addition) {
        return isBlank(current) ? addition : current + " | " + addition;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
