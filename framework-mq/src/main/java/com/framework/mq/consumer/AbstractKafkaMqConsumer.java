package com.framework.mq.consumer;

import com.framework.core.constant.FrameworkConstants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Kafka ConsumerRecord adapter for MessageWrapper messages.
 */
public abstract class AbstractKafkaMqConsumer<T> extends AbstractMessageWrapperConsumer<T> {

    protected AbstractKafkaMqConsumer(StringRedisTemplate redisTemplate, Class<T> payloadType) {
        super(redisTemplate, payloadType);
    }

    public boolean handleRecord(ConsumerRecord<String, String> record) throws Exception {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        return consume(record.value(), traceId(record), record.key());
    }

    private String traceId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(FrameworkConstants.TRACE_ID_HEADER);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
