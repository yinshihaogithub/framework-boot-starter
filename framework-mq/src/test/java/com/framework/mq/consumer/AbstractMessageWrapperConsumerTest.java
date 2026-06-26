package com.framework.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
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
    void trimsIdempotentKeyBeforeCheckingAndMarkingRedis() throws Exception {
        RecordingRedisTemplate redisTemplate = new RecordingRedisTemplate();
        RecordingConsumer consumer = new RecordingConsumer(redisTemplate);
        MessageWrapper<String> wrapper = MessageWrapper.of(" ORDER-9 ", "OrderCreated", "payload");

        boolean consumed = consumer.consume(objectMapper.writeValueAsString(wrapper));

        assertThat(consumed).isTrue();
        assertThat(consumer.runs).hasValue(1);
        assertThat(redisTemplate.lastHasKey).isEqualTo("framework:mq:consumed:ORDER-9");
        assertThat(redisTemplate.lastSetKey).isEqualTo("framework:mq:consumed:ORDER-9");
        assertThat(redisTemplate.lastSetValue).isEqualTo("1");
        assertThat(redisTemplate.lastSetTimeout).isEqualTo(7L);
        assertThat(redisTemplate.lastSetUnit).isEqualTo(TimeUnit.DAYS);
    }

    @Test
    void fallsBackToHeaderTraceWhenWrapperTraceIsUnsafe() throws Exception {
        RecordingConsumer consumer = new RecordingConsumer(null);
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-10", "OrderCreated", "payload");
        wrapper.setTraceId("bad\ntrace");

        boolean consumed = consumer.consume(objectMapper.writeValueAsString(wrapper), " header-trace ", "ORDER-10");

        assertThat(consumed).isTrue();
        assertThat(consumer.observedTrace).hasValue("header-trace");
    }

    private static class RecordingConsumer extends AbstractMessageWrapperConsumer<String> {

        private final AtomicInteger runs = new AtomicInteger();
        private final AtomicReference<String> observedTrace = new AtomicReference<>();

        RecordingConsumer(StringRedisTemplate redisTemplate) {
            super(redisTemplate, String.class);
        }

        @Override
        protected void doConsume(MessageWrapper<String> wrapper) {
            runs.incrementAndGet();
            observedTrace.set(TraceContext.getTraceId());
        }
    }

    private static class RecordingRedisTemplate extends StringRedisTemplate {

        private String lastHasKey;
        private String lastSetKey;
        private String lastSetValue;
        private long lastSetTimeout;
        private TimeUnit lastSetUnit;

        @Override
        public Boolean hasKey(String key) {
            lastHasKey = key;
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("set".equals(method.getName()) && args != null && args.length == 4) {
                            lastSetKey = (String) args[0];
                            lastSetValue = (String) args[1];
                            lastSetTimeout = (Long) args[2];
                            lastSetUnit = (TimeUnit) args[3];
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
