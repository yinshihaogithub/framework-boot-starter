package com.framework.mq.consumer;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;
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
        if (body == null || body.isBlank()) {
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
        if (redisTemplate == null || key == null || key.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(IDEMPOTENT_PREFIX + key));
    }

    private void markConsumed(String key) {
        if (redisTemplate == null || key == null || key.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(IDEMPOTENT_PREFIX + key, "1", 7, TimeUnit.DAYS);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isBlank()) {
                    return trimmed;
                }
            }
        }
        return null;
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
}
