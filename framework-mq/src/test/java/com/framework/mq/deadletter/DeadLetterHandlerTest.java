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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadLetterHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void constructorRejectsNullDependencies() {
        InMemoryRepository repository = new InMemoryRepository();
        MqProperties properties = new MqProperties();

        assertThatThrownBy(() -> new DeadLetterHandler(null, properties))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository");
        assertThatThrownBy(() -> new DeadLetterHandler(repository, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties");
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

    @Test
    void handleDeadLetterDecodesByteArrayTraceHeader() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-10", "OrderCreated", "订单创建");
        wrapper.setTraceId("wrapper-trace");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(100L);
        properties.setMessageId("rabbit-msg-2");
        properties.setReceivedExchange("order.exchange");
        properties.setReceivedRoutingKey("order.created");
        properties.setHeader(FrameworkConstants.TRACE_ID_HEADER, "byte-trace-001".getBytes(StandardCharsets.UTF_8));
        Message message = new Message(
                objectMapper.writeValueAsString(wrapper).getBytes(StandardCharsets.UTF_8),
                properties);
        RecordingChannel channel = new RecordingChannel();

        handler.handleDeadLetter(message, channel.proxy());

        assertThat(channel.ackedDeliveryTag()).isEqualTo(100L);
        assertThat(repository.saved()).hasSize(1);
        assertThat(repository.saved().get(0).getTraceId()).isEqualTo("byte-trace-001");
    }

    @Test
    void handleDeadLetterFallsBackToWrapperTraceWhenHeaderIsInvalid() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-11", "OrderCreated", "订单创建");
        wrapper.setTraceId("wrapper-trace-002");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(101L);
        properties.setMessageId("rabbit-msg-3");
        properties.setReceivedExchange("order.exchange");
        properties.setReceivedRoutingKey("order.created");
        properties.setHeader(FrameworkConstants.TRACE_ID_HEADER, "bad\ntrace");
        Message message = new Message(
                objectMapper.writeValueAsString(wrapper).getBytes(StandardCharsets.UTF_8),
                properties);
        RecordingChannel channel = new RecordingChannel();

        handler.handleDeadLetter(message, channel.proxy());

        assertThat(channel.ackedDeliveryTag()).isEqualTo(101L);
        assertThat(repository.saved()).hasSize(1);
        assertThat(repository.saved().get(0).getTraceId()).isEqualTo("wrapper-trace-002");
    }

    @Test
    void recordConsumeFailureUsesExceptionClassWhenMessageIsBlank() {
        InMemoryRepository repository = new InMemoryRepository();
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        TraceContext.putTraceId("consume-trace");

        handler.recordConsumeFailure(
                "msg-2",
                "order.exchange",
                "order.created",
                "order.queue",
                "{\"id\":2}",
                new IllegalStateException(),
                1,
                3
        );

        assertThat(repository.saved()).hasSize(1);
        MqFailedMessage record = repository.saved().get(0);
        assertThat(record.getTraceId()).isEqualTo("consume-trace");
        assertThat(record.getErrorMessage()).isEqualTo("IllegalStateException");
        assertThat(record.getErrorStack()).contains("IllegalStateException");
        assertThat(record.getStatus()).isEqualTo(MqFailedMessage.STATUS_PENDING);
    }

    @Test
    void recordConsumeFailureStoresBoundedStackSummary() {
        InMemoryRepository repository = new InMemoryRepository();
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        IllegalStateException exception = new IllegalStateException("database unavailable");
        exception.setStackTrace(stackTrace(100));

        handler.recordConsumeFailure(
                "msg-3",
                "order.exchange",
                "order.created",
                "order.queue",
                "{\"id\":3}",
                exception,
                1,
                3
        );

        MqFailedMessage record = repository.saved().get(0);
        assertThat(record.getErrorStack())
                .contains("java.lang.IllegalStateException: database unavailable")
                .contains("at com.framework.demo.Service0.handle(Service0.java:10)")
                .contains("... 80 more")
                .doesNotContain("Service99");
        assertThat(record.getErrorStack()).hasSizeLessThanOrEqualTo(4000);
    }

    @Test
    void removeRecordKeepsMemoryWhenRepositoryDeleteFails() {
        InMemoryRepository repository = new InMemoryRepository(List.of(
                failedMessage(1L, MqFailedMessage.STATUS_EXHAUSTED)));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        repository.failDeleteById = true;

        assertThatThrownBy(() -> handler.removeRecord(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("delete failed");

        assertThat(handler.getById(1L)).isNotNull();
        assertThat(repository.findById(1L)).isPresent();
    }

    @Test
    void cleanProcessedRecordsKeepsMemoryWhenRepositoryDeleteFails() {
        InMemoryRepository repository = new InMemoryRepository(List.of(
                failedMessage(1L, MqFailedMessage.STATUS_PENDING),
                failedMessage(2L, MqFailedMessage.STATUS_SUCCESS),
                failedMessage(3L, MqFailedMessage.STATUS_MANUAL),
                failedMessage(4L, MqFailedMessage.STATUS_EXHAUSTED)));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        repository.failDeleteProcessed = true;

        assertThatThrownBy(handler::cleanProcessedRecords)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("delete processed failed");

        assertThat(handler.getFailedMessageStore().keySet()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(repository.findRecent(Integer.MAX_VALUE))
                .extracting(MqFailedMessage::getId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void constructorRestoresOnlyConfiguredRecentMessages() {
        InMemoryRepository repository = new InMemoryRepository(List.of(
                failedMessage(1L, MqFailedMessage.STATUS_PENDING, new Date(1_000L)),
                failedMessage(2L, MqFailedMessage.STATUS_PENDING, new Date(3_000L)),
                failedMessage(3L, MqFailedMessage.STATUS_PENDING, new Date(2_000L))));
        MqProperties properties = new MqProperties();
        properties.getDeadLetter().setRestoreLimit(2);

        DeadLetterHandler handler = new DeadLetterHandler(repository, properties);

        assertThat(handler.getFailedMessageStore().keySet()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void getByIdLazyLoadsRecordOutsideStartupRestoreWindow() {
        InMemoryRepository repository = new InMemoryRepository(List.of(
                failedMessage(1L, MqFailedMessage.STATUS_PENDING, new Date(1_000L)),
                failedMessage(2L, MqFailedMessage.STATUS_PENDING, new Date(2_000L))));
        MqProperties properties = new MqProperties();
        properties.getDeadLetter().setRestoreLimit(1);
        DeadLetterHandler handler = new DeadLetterHandler(repository, properties);

        assertThat(handler.getFailedMessageStore().keySet()).containsExactly(2L);

        MqFailedMessage loaded = handler.getById(1L);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo(1L);
        assertThat(handler.getFailedMessageStore().keySet()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(handler.getById(null)).isNull();
    }

    private static class InMemoryRepository implements MqFailedMessageRepository {

        private final List<MqFailedMessage> saved = new ArrayList<>();
        private long nextId = 1L;
        private boolean failDeleteById;
        private boolean failDeleteProcessed;

        private InMemoryRepository() {
        }

        private InMemoryRepository(List<MqFailedMessage> initialMessages) {
            saved.addAll(initialMessages);
            initialMessages.stream()
                    .map(MqFailedMessage::getId)
                    .filter(id -> id != null && id >= nextId)
                    .forEach(id -> nextId = id + 1);
        }

        @Override
        public MqFailedMessage save(MqFailedMessage message) {
            if (message.getId() == null) {
                message.setId(nextId++);
            }
            saved.add(message);
            return message;
        }

        @Override
        public boolean update(MqFailedMessage message) {
            return saved.removeIf(savedMessage -> message.getId().equals(savedMessage.getId()))
                    && saved.add(message);
        }

        @Override
        public Optional<MqFailedMessage> findById(Long id) {
            return saved.stream().filter(message -> id.equals(message.getId())).findFirst();
        }

        @Override
        public List<MqFailedMessage> findRecent(int limit) {
            return saved.stream()
                    .sorted(Comparator
                            .comparing(MqFailedMessage::getCreateTime,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(MqFailedMessage::getId,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean deleteById(Long id) {
            if (failDeleteById) {
                throw new IllegalStateException("delete failed");
            }
            return saved.removeIf(message -> id.equals(message.getId()));
        }

        @Override
        public int deleteProcessed() {
            if (failDeleteProcessed) {
                throw new IllegalStateException("delete processed failed");
            }
            int before = saved.size();
            saved.removeIf(DeadLetterHandlerTest::isProcessedRecord);
            return before - saved.size();
        }

        List<MqFailedMessage> saved() {
            return saved;
        }
    }

    private static MqFailedMessage failedMessage(Long id, String status) {
        return failedMessage(id, status, null);
    }

    private static MqFailedMessage failedMessage(Long id, String status, Date createTime) {
        MqFailedMessage message = new MqFailedMessage();
        message.setId(id);
        message.setMessageId("msg-" + id);
        message.setStatus(status);
        message.setCreateTime(createTime);
        return message;
    }

    private static boolean isProcessedRecord(MqFailedMessage message) {
        return MqFailedMessage.STATUS_SUCCESS.equals(message.getStatus())
                || MqFailedMessage.STATUS_EXHAUSTED.equals(message.getStatus())
                || MqFailedMessage.STATUS_MANUAL.equals(message.getStatus());
    }

    private static StackTraceElement[] stackTrace(int size) {
        StackTraceElement[] stackTrace = new StackTraceElement[size];
        for (int i = 0; i < size; i++) {
            stackTrace[i] = new StackTraceElement(
                    "com.framework.demo.Service" + i,
                    "handle",
                    "Service" + i + ".java",
                    10 + i);
        }
        return stackTrace;
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
