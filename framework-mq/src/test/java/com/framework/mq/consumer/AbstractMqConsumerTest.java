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
        wrapper.setTraceId("wrapper-trace");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(100L);
        properties.setMessageId("rabbit-message-id");
        properties.setHeader(FrameworkConstants.TRACE_ID_HEADER, "header-trace");
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
}
