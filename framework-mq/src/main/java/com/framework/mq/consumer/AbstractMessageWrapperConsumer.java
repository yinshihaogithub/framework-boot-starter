package com.framework.mq.consumer;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;
import com.framework.mq.support.MqTextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Provider-neutral MessageWrapper consumer base.
 */
@Slf4j
public abstract class AbstractMessageWrapperConsumer<T> {

    private static final String IDEMPOTENT_PREFIX = "framework:mq:consumed:";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringRedisTemplate redisTemplate;
    private final Class<T> payloadType;

    protected AbstractMessageWrapperConsumer(StringRedisTemplate redisTemplate, Class<T> payloadType) {
        this.redisTemplate = redisTemplate;
        this.payloadType = payloadType;
    }

    protected abstract void doConsume(MessageWrapper<T> wrapper) throws Exception;

    public boolean consume(String body) throws Exception {
        return consume(body, null, null);
    }

    public boolean consume(String body, String headerTraceId, String fallbackMessageId) throws Exception {
        if (!MqTextSupport.hasText(body)) {
            throw new IllegalArgumentException("message body must not be blank");
        }
        Map<String, String> previousContext = TraceContext.copyContextMap();
        try {
            MessageWrapper<T> wrapper = decode(body);
            TraceContext.getOrCreateTraceId(firstTraceId(wrapper.getTraceId(), headerTraceId));

            String idempotentKey = firstText(wrapper.getBusinessKey(), fallbackMessageId, wrapper.getMessageId());
            if (isAlreadyConsumed(idempotentKey)) {
                log.warn("[MQ消费] 重复消息已消费，跳过 key={}", idempotentKey);
                return false;
            }

            doConsume(wrapper);
            markConsumed(idempotentKey);
            return true;
        } finally {
            TraceContext.restore(previousContext);
        }
    }

    protected MessageWrapper<T> decode(String body) throws Exception {
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(
                MessageWrapper.class, payloadType);
        return objectMapper.readValue(body, javaType);
    }

    private boolean isAlreadyConsumed(String key) {
        String normalizedKey = MqTextSupport.trimToNull(key);
        if (redisTemplate == null || normalizedKey == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(IDEMPOTENT_PREFIX + normalizedKey));
        } catch (Exception e) {
            log.warn("[MQ消费] Redis幂等检查失败 key={} error={}", normalizedKey, e.getMessage());
            return false;
        }
    }

    private void markConsumed(String key) {
        String normalizedKey = MqTextSupport.trimToNull(key);
        if (redisTemplate == null || normalizedKey == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(IDEMPOTENT_PREFIX + normalizedKey, "1", 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("[MQ消费] Redis幂等标记失败 key={} error={}", normalizedKey, e.getMessage());
        }
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
