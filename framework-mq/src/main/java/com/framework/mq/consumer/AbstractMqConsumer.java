package com.framework.mq.consumer;

import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;
import com.framework.mq.support.MqTextSupport;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 消费者基类
 * 提供以下能力：
 * 1. 消息反序列化
 * 2. 幂等消费（基于 Redis 去重）
 * 3. 手动 ACK / NACK
 * 4. 重试次数控制
 * 5. 死信兜底
 *
 * 子类只需实现 doConsume 方法，并用 @RabbitListener 注解标注监听方法
 */
@Slf4j
public abstract class AbstractMqConsumer<T> {

    private static final String IDEMPOTENT_PREFIX = "framework:mq:consumed:";
    private static final int MAX_RETRY_COUNT = 3;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringRedisTemplate redisTemplate;
    private final Class<T> payloadType;

    protected AbstractMqConsumer(StringRedisTemplate redisTemplate, Class<T> payloadType) {
        this.redisTemplate = redisTemplate;
        this.payloadType = payloadType;
    }

    /**
     * 子类实现：业务消费逻辑
     *
     * @param wrapper 消息包装器
     * @throws Exception 业务异常会触发重试
     */
    protected abstract void doConsume(MessageWrapper<T> wrapper) throws Exception;

    /**
     * 消息处理入口
     * 子类在 @RabbitListener 方法中调用此方法
     *
     * 示例:
     * @RabbitListener(queues = "order.queue")
     * public void handle(Message message, Channel channel) throws Exception {
     *     handleMessage(message, channel);
     * }
     */
    public void handleMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = MqTextSupport.trimToNull(message.getMessageProperties().getMessageId());
        Integer retryCount = getRetryCount(message);
        Map<String, String> previousContext = TraceContext.copyContextMap();

        try {
            String headerTraceId = getHeader(message, FrameworkConstants.TRACE_ID_HEADER);
            if (headerTraceId != null) {
                TraceContext.getOrCreateTraceId(headerTraceId);
            }

            // 反序列化
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            if (!MqTextSupport.hasText(body)) {
                throw new IllegalArgumentException("message body must not be blank");
            }
            JavaType javaType = objectMapper.getTypeFactory().constructParametricType(
                    MessageWrapper.class, payloadType);
            MessageWrapper<T> wrapper = objectMapper.readValue(body, javaType);
            TraceContext.getOrCreateTraceId(firstTraceId(wrapper.getTraceId(), headerTraceId));

            // 使用 messageId 或 businessKey 做幂等
            String idempotentKey = firstText(wrapper.getBusinessKey(), messageId, wrapper.getMessageId());

            // 幂等检查
            if (isAlreadyConsumed(idempotentKey)) {
                log.warn("[MQ消费] 重复消息已消费，跳过 key={}", idempotentKey);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 执行消费
            log.info("[MQ消费] messageId={}, traceId={}, type={}, retryCount={}",
                    messageId, TraceContext.getTraceId(), wrapper.getType(), retryCount);
            doConsume(wrapper);

            // 标记已消费（TTL 7天，防止重复消费）
            markConsumed(idempotentKey);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[MQ消费失败] messageId={}, retryCount={}", messageId, retryCount, e);
            handleConsumeFailure(message, channel, deliveryTag, retryCount, e);
        } finally {
            TraceContext.restore(previousContext);
        }
    }

    /**
     * 消费失败处理：重试 or 死信
     */
    private void handleConsumeFailure(Message message, Channel channel,
                                      long deliveryTag, Integer retryCount, Exception e) throws Exception {
        if (retryCount < MAX_RETRY_COUNT) {
            // NACK（不重新入队），由死信交换机路由到重试队列
            // 重试队列设置 TTL，超时后再次转发到原队列实现延迟重试
            log.warn("[MQ重试] deliveryTag={}, 当前重试次数={}, 最大重试={}", deliveryTag, retryCount, MAX_RETRY_COUNT);
            channel.basicNack(deliveryTag, false, false);
        } else {
            // 超过最大重试次数，ACK 并记录到死信（由死信队列兜底）
            log.error("[MQ死信] 超过最大重试次数={}, 消息将进入死信队列", MAX_RETRY_COUNT);
            channel.basicAck(deliveryTag, false);
        }
    }

    /**
     * 幂等检查：Redis SETNX
     */
    private boolean isAlreadyConsumed(String key) {
        String normalizedKey = MqTextSupport.trimToNull(key);
        if (redisTemplate == null || normalizedKey == null) {
            return false;
        }
        String redisKey = IDEMPOTENT_PREFIX + normalizedKey;
        try {
            // key 已存在说明已消费过
            return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        } catch (Exception e) {
            log.warn("[MQ消费] Redis幂等检查失败 key={} error={}", normalizedKey, e.getMessage());
            return false;
        }
    }

    /**
     * 标记已消费
     */
    private void markConsumed(String key) {
        String normalizedKey = MqTextSupport.trimToNull(key);
        if (redisTemplate == null || normalizedKey == null) {
            return;
        }
        String redisKey = IDEMPOTENT_PREFIX + normalizedKey;
        try {
            redisTemplate.opsForValue().set(redisKey, "1", 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("[MQ消费] Redis幂等标记失败 key={} error={}", normalizedKey, e.getMessage());
        }
    }

    /**
     * 从消息头获取重试次数
     */
    private Integer getRetryCount(Message message) {
        Object retryHeader = message.getMessageProperties().getHeader("x-retry-count");
        if (retryHeader instanceof Integer) {
            return (Integer) retryHeader;
        }
        return 0;
    }

    private String getHeader(Message message, String headerName) {
        Object value = message.getMessageProperties().getHeader(headerName);
        return value == null ? null : MqTextSupport.trimToNull(value.toString());
    }

    private String firstText(String... values) {
        for (String value : values) {
            String trimmed = MqTextSupport.trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String firstTraceId(String... values) {
        for (String value : values) {
            String traceId = TraceContext.normalizeTraceId(MqTextSupport.trimToNull(value));
            if (traceId != null) {
                return traceId;
            }
        }
        return null;
    }
}
