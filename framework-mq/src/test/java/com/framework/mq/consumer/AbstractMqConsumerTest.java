package com.framework.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractMqConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void consumesRabbitMessageWithoutRedisAndRestoresPreviousContext() throws Exception {
        RecordingRabbitConsumer consumer = new RecordingRabbitConsumer();
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-3", "OrderCreated", "订单创建");
        wrapper.setTraceId("\u00A0wrapper-trace\u3000");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(100L);
        properties.setMessageId("rabbit-message-id");
        properties.setHeader(FrameworkConstants.TRACE_ID_HEADER, "\u00A0header-trace\u3000");
        Message message = new Message(
                objectMapper.writeValueAsString(wrapper).getBytes(StandardCharsets.UTF_8),
                properties);
        RecordingChannel channel = new RecordingChannel();
        TraceContext.putTraceId("caller-trace");

        consumer.handleMessage(message, channel.proxy());

        assertThat(consumer.observedTrace()).hasValue("wrapper-trace");
        assertThat(consumer.lastWrapper().getPayload()).isEqualTo("订单创建");
        assertThat(TraceContext.getTraceId()).isEqualTo("caller-trace");
        assertThat(channel.ackedDeliveryTag()).isEqualTo(100L);
        assertThat(channel.nacked()).isFalse();
    }

    @Test
    void fallsBackToTrimmedRabbitHeaderTraceWhenWrapperTraceIsUnsafe() throws Exception {
        RecordingRabbitConsumer consumer = new RecordingRabbitConsumer();
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-31", "OrderCreated", "订单创建");
        wrapper.setTraceId("bad\ntrace");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(103L);
        properties.setMessageId("rabbit-message-id");
        properties.setHeader(FrameworkConstants.TRACE_ID_HEADER, "\u00A0header-trace\u3000");
        Message message = new Message(
                objectMapper.writeValueAsString(wrapper).getBytes(StandardCharsets.UTF_8),
                properties);
        RecordingChannel channel = new RecordingChannel();

        consumer.handleMessage(message, channel.proxy());

        assertThat(consumer.observedTrace()).hasValue("header-trace");
        assertThat(channel.ackedDeliveryTag()).isEqualTo(103L);
        assertThat(channel.nacked()).isFalse();
    }

    @Test
    void fallsBackToTrimmedRabbitMessageIdForIdempotentKeyWhenBusinessKeyIsBlank() throws Exception {
        RecordingRedisTemplate redisTemplate = new RecordingRedisTemplate();
        RecordingRabbitConsumer consumer = new RecordingRabbitConsumer(redisTemplate);
        MessageWrapper<String> wrapper = MessageWrapper.of("\u00A0\u3000", "OrderCreated", "订单创建");
        wrapper.setTraceId("wrapper-trace");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(104L);
        properties.setMessageId("\u00A0rabbit-message-id\u3000");
        Message message = new Message(
                objectMapper.writeValueAsString(wrapper).getBytes(StandardCharsets.UTF_8),
                properties);
        RecordingChannel channel = new RecordingChannel();

        consumer.handleMessage(message, channel.proxy());

        assertThat(redisTemplate.lastHasKey).isEqualTo("framework:mq:consumed:rabbit-message-id");
        assertThat(redisTemplate.lastSetKey).isEqualTo("framework:mq:consumed:rabbit-message-id");
        assertThat(redisTemplate.lastSetValue).isEqualTo("1");
        assertThat(redisTemplate.lastSetTimeout).isEqualTo(7L);
        assertThat(redisTemplate.lastSetUnit).isEqualTo(TimeUnit.DAYS);
        assertThat(channel.ackedDeliveryTag()).isEqualTo(104L);
        assertThat(channel.nacked()).isFalse();
    }

    @Test
    void redisIdempotentFailuresDoNotBlockRabbitConsumption() throws Exception {
        RecordingRabbitConsumer consumer = new RecordingRabbitConsumer(new ThrowingRedisTemplate());
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-4", "OrderCreated", "订单创建");
        wrapper.setTraceId("wrapper-trace");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(101L);
        properties.setMessageId("rabbit-message-id");
        Message message = new Message(
                objectMapper.writeValueAsString(wrapper).getBytes(StandardCharsets.UTF_8),
                properties);
        RecordingChannel channel = new RecordingChannel();

        consumer.handleMessage(message, channel.proxy());

        assertThat(consumer.lastWrapper().getPayload()).isEqualTo("订单创建");
        assertThat(channel.ackedDeliveryTag()).isEqualTo(101L);
        assertThat(channel.nacked()).isFalse();
    }

    private static class RecordingRabbitConsumer extends AbstractMqConsumer<String> {

        private final AtomicReference<String> observedTrace = new AtomicReference<>();
        private MessageWrapper<String> lastWrapper;

        RecordingRabbitConsumer() {
            super(null, String.class);
        }

        RecordingRabbitConsumer(StringRedisTemplate redisTemplate) {
            super(redisTemplate, String.class);
        }

        @Override
        protected void doConsume(MessageWrapper<String> wrapper) {
            observedTrace.set(TraceContext.getTraceId());
            lastWrapper = wrapper;
        }

        AtomicReference<String> observedTrace() {
            return observedTrace;
        }

        MessageWrapper<String> lastWrapper() {
            return lastWrapper;
        }
    }

    private static class RecordingChannel {

        private Long ackedDeliveryTag;
        private boolean nacked;

        Channel proxy() {
            return (Channel) Proxy.newProxyInstance(
                    Channel.class.getClassLoader(),
                    new Class<?>[]{Channel.class},
                    (proxy, method, args) -> {
                        if ("basicAck".equals(method.getName())) {
                            ackedDeliveryTag = (Long) args[0];
                            return null;
                        }
                        if ("basicNack".equals(method.getName())) {
                            nacked = true;
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        Long ackedDeliveryTag() {
            return ackedDeliveryTag;
        }

        boolean nacked() {
            return nacked;
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

    private static class ThrowingRedisTemplate extends StringRedisTemplate {

        @Override
        public Boolean hasKey(String key) {
            throw unavailable();
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("set".equals(method.getName())) {
                            throw unavailable();
                        }
                        return RecordingChannel.defaultValue(method.getReturnType());
                    });
        }

        private static IllegalStateException unavailable() {
            return new IllegalStateException("redis unavailable");
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
                        return RecordingChannel.defaultValue(method.getReturnType());
                    });
        }
    }
}
