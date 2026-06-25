package com.framework.mq.consumer;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * RocketMQ string-body adapter for MessageWrapper messages.
 */
public abstract class AbstractRocketMqConsumer<T> extends AbstractMessageWrapperConsumer<T> {

    protected AbstractRocketMqConsumer(StringRedisTemplate redisTemplate, Class<T> payloadType) {
        super(redisTemplate, payloadType);
    }

    public boolean handleMessage(String body) throws Exception {
        return consume(body, null, null);
    }

    public boolean handleMessage(String body, String traceId, String fallbackMessageId) throws Exception {
        return consume(body, traceId, fallbackMessageId);
    }
}
