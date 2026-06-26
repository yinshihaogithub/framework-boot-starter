package com.framework.mq.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import com.framework.mq.config.MqProperties;
import com.framework.mq.core.MessageWrapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void handleDeadLetterPersistsRecordAndRestoresPreviousTraceContext() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-9", "OrderCreated", "订单创建");
        wrapper.setTraceId("wrapper-trace");
        wrapper.setParentMessageId("parent-msg-1");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(99L);
        properties.setMessageId("rabbit-msg-1");
        properties.setReceivedExchange("order.exchange");
        properties.setReceivedRoutingKey("order.created");
        properties.setHeader(FrameworkConstants.TRACE_ID_HEADER, "dead-trace");
        properties.setHeader("x-first-death-queue", "order.dead.queue");
        Message message = new Message(
                objectMapper.writeValueAsString(wrapper).getBytes(StandardCharsets.UTF_8),
                properties);
        RecordingChannel channel = new RecordingChannel();
        TraceContext.putTraceId("caller-trace");

        handler.handleDeadLetter(message, channel.proxy());

        assertThat(TraceContext.getTraceId()).isEqualTo("caller-trace");
        assertThat(channel.ackedDeliveryTag()).isEqualTo(99L);
        assertThat(repository.saved()).hasSize(1);
        MqFailedMessage record = repository.saved().get(0);
        assertThat(record.getMessageId()).isEqualTo("rabbit-msg-1");
        assertThat(record.getTraceId()).isEqualTo("dead-trace");
        assertThat(record.getParentMessageId()).isEqualTo("parent-msg-1");
        assertThat(record.getBusinessKey()).isEqualTo("ORDER-9");
        assertThat(record.getMessageType()).isEqualTo("OrderCreated");
        assertThat(record.getQueueName()).isEqualTo("order.dead.queue");
        assertThat(record.getPayload()).contains("订单创建");
    }

    private static class InMemoryRepository implements MqFailedMessageRepository {

        private final List<MqFailedMessage> saved = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public MqFailedMessage save(MqFailedMessage message) {
            if (message.getId() == null) {
                message.setId(nextId++);
            }
            saved.add(message);
            return message;
        }

        @Override
        public Optional<MqFailedMessage> findById(Long id) {
            return saved.stream().filter(message -> id.equals(message.getId())).findFirst();
        }

        @Override
        public List<MqFailedMessage> findAll() {
            return List.of();
        }

        @Override
        public boolean deleteById(Long id) {
            return saved.removeIf(message -> id.equals(message.getId()));
        }

        @Override
        public int deleteProcessed() {
            return 0;
        }

        List<MqFailedMessage> saved() {
            return saved;
        }
    }

    private static class RecordingChannel {

        private Long ackedDeliveryTag;

        Channel proxy() {
            return (Channel) Proxy.newProxyInstance(
                    Channel.class.getClassLoader(),
                    new Class<?>[]{Channel.class},
                    (proxy, method, args) -> {
                        if ("basicAck".equals(method.getName())) {
                            ackedDeliveryTag = (Long) args[0];
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        Long ackedDeliveryTag() {
            return ackedDeliveryTag;
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
}
