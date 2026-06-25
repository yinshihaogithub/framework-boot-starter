package com.framework.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractMessageWrapperConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void consumesWrapperRestoresTraceAndMarksIdempotency() throws Exception {
        InMemoryStringRedisTemplate redisTemplate = new InMemoryStringRedisTemplate();
        RecordingConsumer consumer = new RecordingConsumer(redisTemplate);
        TraceContext.getOrCreateTraceId("caller-trace");
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-1", "OrderCreated", "payload");
        wrapper.setTraceId("message-trace");

        boolean consumed = consumer.consume(objectMapper.writeValueAsString(wrapper), null, null);

        assertThat(consumed).isTrue();
        assertThat(consumer.count()).hasValue(1);
        assertThat(consumer.observedTrace()).hasValue("message-trace");
        assertThat(consumer.lastWrapper().getBusinessKey()).isEqualTo("ORDER-1");
        assertThat(redisTemplate.hasKey("framework:mq:consumed:ORDER-1")).isTrue();
        assertThat(TraceContext.getTraceId()).isEqualTo("caller-trace");
    }

    @Test
    void skipsDuplicateMessageByBusinessKey() throws Exception {
        InMemoryStringRedisTemplate redisTemplate = new InMemoryStringRedisTemplate();
        redisTemplate.markConsumed("framework:mq:consumed:ORDER-1");
        RecordingConsumer consumer = new RecordingConsumer(redisTemplate);
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-1", "OrderCreated", "payload");

        boolean consumed = consumer.consume(objectMapper.writeValueAsString(wrapper), null, null);

        assertThat(consumed).isFalse();
        assertThat(consumer.count()).hasValue(0);
    }

    @Test
    void usesHeaderTraceWhenWrapperTraceIsMissing() throws Exception {
        RecordingConsumer consumer = new RecordingConsumer(new InMemoryStringRedisTemplate());
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-1", "OrderCreated", "payload");
        wrapper.setTraceId(null);

        boolean consumed = consumer.consume(objectMapper.writeValueAsString(wrapper), "header-trace", null);

        assertThat(consumed).isTrue();
        assertThat(consumer.observedTrace()).hasValue("header-trace");
    }

    @Test
    void consumesWithoutRedisIdempotencyStore() throws Exception {
        RecordingConsumer consumer = new RecordingConsumer(null);
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-1", "OrderCreated", "payload");

        boolean consumed = consumer.consume(objectMapper.writeValueAsString(wrapper), null, null);

        assertThat(consumed).isTrue();
        assertThat(consumer.count()).hasValue(1);
    }

    private static class RecordingConsumer extends AbstractMessageWrapperConsumer<String> {

        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<String> observedTrace = new AtomicReference<>();
        private MessageWrapper<String> lastWrapper;

        RecordingConsumer(StringRedisTemplate redisTemplate) {
            super(redisTemplate, String.class);
        }

        @Override
        protected void doConsume(MessageWrapper<String> wrapper) {
            count.incrementAndGet();
            observedTrace.set(TraceContext.getTraceId());
            lastWrapper = wrapper;
        }

        AtomicInteger count() {
            return count;
        }

        AtomicReference<String> observedTrace() {
            return observedTrace;
        }

        MessageWrapper<String> lastWrapper() {
            return lastWrapper;
        }
    }

    private static class InMemoryStringRedisTemplate extends StringRedisTemplate {

        private final Set<String> keys = new HashSet<>();

        @Override
        public Boolean hasKey(String key) {
            return keys.contains(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("set".equals(method.getName()) && args.length >= 2) {
                            keys.add((String) args[0]);
                        }
                        return null;
                    });
        }

        void markConsumed(String key) {
            keys.add(key);
        }
    }
}
