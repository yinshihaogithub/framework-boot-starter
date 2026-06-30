package com.framework.mq.deadletter;

import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.mq.config.MqProperties;
import com.framework.mq.core.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 死信处理器
 * 监听死信队列，将死信消息持久化到 MySQL，等待重试或人工处理
 *
 * 消费失败 → NACK → 死信交换机 → 死信队列 → DeadLetterHandler → 记录到 MySQL
 */
@Slf4j
public class DeadLetterHandler {

    private final MqFailedMessageRepository repository;
    private final MqProperties properties;
    private final ObjectMapper objectMapper;

    /** 内存缓存索引（启动时从 MySQL 加载，读写同步到 MySQL） */
    private final ConcurrentMap<Long, MqFailedMessage> failedMessageStore = new ConcurrentHashMap<>();

    public DeadLetterHandler(MqFailedMessageRepository repository, MqProperties properties) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = new ObjectMapper();
        loadFromRepository();
    }

    /**
     * 启动时从 MySQL 加载历史失败消息到内存缓存
     */
    private void loadFromRepository() {
        try {
            for (MqFailedMessage msg : repository.findAll()) {
                failedMessageStore.put(msg.getId(), msg);
            }
            log.info("[死信处理器] 从 MySQL 恢复 {} 条失败消息记录", failedMessageStore.size());
        } catch (Exception e) {
            log.warn("[死信处理器] 从 MySQL 加载失败消息异常: {}", failureMessage(e));
        }
    }

    /**
     * 处理死信消息
     * 由 @RabbitListener(queues = "dead.letter.queue") 调用
     */
    public void handleDeadLetter(Message message, com.rabbitmq.client.Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        MessageProperties props = message.getMessageProperties();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Map<String, String> previousContextMap = TraceContext.copyContextMap();

        try {
            // 解析死信信息
            String originalQueue = props.getReceivedRoutingKey();
            String originalExchange = props.getReceivedExchange();
            String messageId = props.getMessageId();
            String traceId = extractTraceId(props, body);
            TraceContext.getOrCreateTraceId(traceId);

            // 从 x-death header 获取死信原因
            String deathReason = extractDeathReason(props);
            String firstDeathQueue = props.getHeader("x-first-death-queue");

            // 创建失败记录
            MqFailedMessage record = new MqFailedMessage();
            record.setMessageId(messageId);
            record.setTraceId(TraceContext.getTraceId());
            record.setExchange(originalExchange);
            record.setRoutingKey(props.getReceivedRoutingKey());
            record.setQueueName(firstDeathQueue != null ? firstDeathQueue : originalQueue);
            record.setPayload(body);
            record.setErrorMessage("死信原因: " + deathReason);
            record.setErrorStack(extractDeathDetail(props));
            record.setRetryCount(0);
            record.setMaxRetry(properties.getMaxRetry());
            record.setStatus(MqFailedMessage.STATUS_PENDING);
            record.setSource(MqFailedMessage.SOURCE_DEAD_LETTER);
            record.setNextRetryTime(calculateNextRetryTime(0));
            record.setCreateTime(new Date());
            record.setUpdateTime(new Date());
            fillWrapperMetadata(record, body);

            saveRecord(record);

            log.error("[死信处理] messageId={}, traceId={}, queue={}, reason={}",
                    messageId, record.getTraceId(), record.getQueueName(), deathReason);

            // ACK 死信（已记录，不再重新投递）
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[死信处理失败]", e);
            // 处理失败也 ACK，避免死信循环
            channel.basicAck(deliveryTag, false);
        } finally {
            TraceContext.restore(previousContextMap);
        }
    }

    /**
     * 消费失败时直接记录（不经过死信队列的场景）
     */
    public void recordConsumeFailure(String messageId, String exchange, String routingKey,
                                     String queueName, String payload,
                                     Exception e, int retryCount, int maxRetry) {
        MqFailedMessage record = new MqFailedMessage();
        record.setMessageId(messageId);
        record.setTraceId(TraceContext.getTraceId());
        record.setExchange(exchange);
        record.setRoutingKey(routingKey);
        record.setQueueName(queueName);
        record.setPayload(payload);
        record.setErrorMessage(failureMessage(e));
        record.setErrorStack(getStackTrace(e));
        record.setRetryCount(retryCount);
        record.setMaxRetry(maxRetry);
        record.setStatus(retryCount >= maxRetry
                ? MqFailedMessage.STATUS_EXHAUSTED
                : MqFailedMessage.STATUS_PENDING);
        record.setSource(MqFailedMessage.SOURCE_CONSUME_FAIL);
        record.setNextRetryTime(calculateNextRetryTime(retryCount));
        record.setCreateTime(new Date());
        record.setUpdateTime(new Date());
        fillWrapperMetadata(record, payload);

        saveRecord(record);

        log.error("[消费失败记录] messageId={}, traceId={}, queue={}, retry={}/{}, status={}",
                messageId, record.getTraceId(), queueName, retryCount, maxRetry, record.getStatus());
    }

    /**
     * 更新重试成功
     */
    public void markRetrySuccess(Long id) {
        MqFailedMessage record = failedMessageStore.get(id);
        if (record != null) {
            record.setStatus(MqFailedMessage.STATUS_SUCCESS);
            record.setUpdateTime(new Date());
            updateRecord(record);
            log.info("[重试成功] id={}, messageId={}", id, record.getMessageId());
        }
    }

    /**
     * 更新记录状态并同步到 MySQL
     */
    public void updateRecord(MqFailedMessage record) {
        MqFailedMessage updated = record.copy();
        updated.setUpdateTime(new Date());
        repository.save(updated);
        failedMessageStore.put(updated.getId(), updated);
    }

    /**
     * 删除记录（从内存和 MySQL）
     */
    public boolean removeRecord(Long id) {
        boolean deleted = repository.deleteById(id);
        MqFailedMessage removed = failedMessageStore.remove(id);
        return removed != null || deleted;
    }

    public int cleanProcessedRecords() {
        int deleted = repository.deleteProcessed();
        long before = failedMessageStore.size();
        failedMessageStore.values().removeIf(DeadLetterHandler::isProcessedRecord);
        int removedFromStore = Math.toIntExact(before - failedMessageStore.size());
        return deleted > 0 ? deleted : removedFromStore;
    }

    // ===== 私有方法 =====

    private String extractDeathReason(MessageProperties props) {
        try {
            var deaths = props.getHeader("x-death");
            if (deaths instanceof java.util.List<?> deathList && !deathList.isEmpty()) {
                var firstDeath = (java.util.Map<?, ?>) deathList.get(0);
                Object reason = firstDeath.get("reason");
                return reason != null ? reason.toString() : "unknown";
            }
        } catch (Exception e) {
            // ignore
        }
        return "unknown";
    }

    private String extractDeathDetail(MessageProperties props) {
        try {
            return objectMapper.writeValueAsString(props.getHeaders());
        } catch (Exception e) {
            return "serialize failed";
        }
    }

    private Date calculateNextRetryTime(int retryCount) {
        // 指数退避：1min, 5min, 30min, 2h, 12h
        long[] intervals = {60_000L, 300_000L, 1_800_000L, 7_200_000L, 43_200_000L};
        long interval = retryCount < intervals.length
                ? intervals[retryCount]
                : intervals[intervals.length - 1];
        return new Date(System.currentTimeMillis() + interval);
    }

    private void saveRecord(MqFailedMessage record) {
        try {
            repository.save(record);
            failedMessageStore.put(record.getId(), record);
        } catch (Exception e) {
            log.warn("持久化失败消息到MySQL失败: {}", failureMessage(e));
        }
    }

    private static boolean isProcessedRecord(MqFailedMessage message) {
        return MqFailedMessage.STATUS_SUCCESS.equals(message.getStatus())
                || MqFailedMessage.STATUS_EXHAUSTED.equals(message.getStatus())
                || MqFailedMessage.STATUS_MANUAL.equals(message.getStatus());
    }

    private String extractTraceId(MessageProperties props, String body) {
        String headerTraceId = normalizeTraceId(props.getHeader(FrameworkConstants.TRACE_ID_HEADER));
        String wrapperTraceId = null;
        try {
            MessageWrapper<?> wrapper = objectMapper.readValue(body, MessageWrapper.class);
            wrapperTraceId = wrapper.getTraceId();
        } catch (Exception e) {
            // Message body may be a raw payload for legacy senders.
        }
        return firstTraceId(headerTraceId, wrapperTraceId);
    }

    private void fillWrapperMetadata(MqFailedMessage record, String body) {
        try {
            MessageWrapper<?> wrapper = objectMapper.readValue(body, MessageWrapper.class);
            if (record.getMessageId() == null) {
                record.setMessageId(wrapper.getMessageId());
            }
            record.setTraceId(firstTraceId(record.getTraceId(), wrapper.getTraceId()));
            record.setParentMessageId(wrapper.getParentMessageId());
            record.setBusinessKey(wrapper.getBusinessKey());
            record.setMessageType(wrapper.getType());
        } catch (Exception e) {
            // Message body may be a raw payload for legacy senders.
        }
    }

    private String firstTraceId(String... values) {
        for (String value : values) {
            String traceId = TraceContext.normalizeTraceId(value);
            if (traceId != null) {
                return traceId;
            }
        }
        return null;
    }

    private String normalizeTraceId(Object value) {
        if (value instanceof byte[] bytes) {
            return TraceContext.normalizeTraceId(new String(bytes, StandardCharsets.UTF_8));
        }
        return value == null ? null : TraceContext.normalizeTraceId(value.toString());
    }

    private String getStackTrace(Throwable e) {
        if (e == null) {
            return null;
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private String failureMessage(Throwable exception) {
        if (exception == null) {
            return "unknown error";
        }
        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        String simpleName = exception.getClass().getSimpleName();
        return simpleName.isBlank() ? exception.getClass().getName() : simpleName;
    }

    // ===== 查询方法（供管理接口调用） =====

    public ConcurrentMap<Long, MqFailedMessage> getFailedMessageStore() {
        return failedMessageStore;
    }

    public MqFailedMessage getById(Long id) {
        return failedMessageStore.get(id);
    }

    /**
     * 获取所有失败消息列表（从内存缓存）
     */
    public List<MqFailedMessage> getAllFailedMessages() {
        return new ArrayList<>(failedMessageStore.values());
    }
}
