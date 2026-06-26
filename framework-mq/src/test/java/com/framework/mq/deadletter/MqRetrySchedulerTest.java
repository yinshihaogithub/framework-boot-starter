package com.framework.mq.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.mq.config.MqProperties;
import com.framework.mq.core.MessageWrapper;
import com.framework.mq.producer.MqMessageSender;
import com.framework.mq.producer.MqMessageSenderRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqRetrySchedulerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void manualRetryPreservesOriginalWrapperMetadataAndRecordsOperator() throws Exception {
        MessageWrapper<String> original = MessageWrapper.of("ORDER-1", "OrderCreated", "payload");
        original.setMessageId("msg-1");
        original.setTraceId("trace-1");
        original.setParentMessageId("parent-1");

        MqFailedMessage failedMessage = failedMessage(objectMapper.writeValueAsString(original));
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        RecordingSender sender = new RecordingSender();
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT), List.of(sender)),
                3
        );

        boolean result = scheduler.manualRetry(1L, " ops-user ", " fixed inventory ");

        assertThat(result).isTrue();
        assertThat(sender.destination).isEqualTo("order.exchange");
        assertThat(sender.routingKey).isEqualTo("order.created");
        assertThat(sender.wrapper.getMessageId()).isEqualTo("msg-1");
        assertThat(sender.wrapper.getTraceId()).isEqualTo("trace-1");
        assertThat(sender.wrapper.getParentMessageId()).isEqualTo("parent-1");
        assertThat(sender.wrapper.getBusinessKey()).isEqualTo("ORDER-1");
        assertThat(sender.wrapper.getType()).isEqualTo("OrderCreated");
        assertThat(sender.wrapper.getPayload()).isEqualTo("payload");

        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MqFailedMessage.STATUS_MANUAL);
        assertThat(saved.getOperator()).isEqualTo("ops-user");
        assertThat(saved.getCompensateRemark()).isEqualTo("fixed inventory");
        assertThat(saved.getRetryCount()).isEqualTo(1);
    }

    @Test
    void manualRetryWrapsLegacyRawPayloadWithFailedMessageMetadata() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        failedMessage.setMessageId("legacy-msg");
        failedMessage.setTraceId("legacy-trace");
        failedMessage.setBusinessKey("ORDER-2");
        failedMessage.setMessageType("LegacyEvent");
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        RecordingSender sender = new RecordingSender();
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT), List.of(sender)),
                3
        );

        boolean result = scheduler.manualRetry(1L, "ops-user");

        assertThat(result).isTrue();
        assertThat(sender.wrapper.getMessageId()).isEqualTo("legacy-msg");
        assertThat(sender.wrapper.getTraceId()).isEqualTo("legacy-trace");
        assertThat(sender.wrapper.getBusinessKey()).isEqualTo("ORDER-2");
        assertThat(sender.wrapper.getType()).isEqualTo("LegacyEvent");
        assertThat(sender.wrapper.getPayload()).isEqualTo("{\"legacy\":true}");
    }

    @Test
    void manualRetryUsesLegacyTypeWhenFailedRecordMissesMessageType() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        failedMessage.setMessageType(null);
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        RecordingSender sender = new RecordingSender();
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT), List.of(sender)),
                3
        );

        boolean result = scheduler.manualRetry(1L, "ops-user");

        assertThat(result).isTrue();
        assertThat(sender.wrapper.getType()).isEqualTo("LegacyMessage");
    }

    @Test
    void scanAndRetryTreatsNullNextRetryTimeAsDueImmediately() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        failedMessage.setNextRetryTime(null);
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        RecordingSender sender = new RecordingSender();
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT), List.of(sender)),
                3
        );

        scheduler.scanAndRetry();

        assertThat(sender.wrapper).isNotNull();
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(MqFailedMessage.STATUS_PENDING);
        assertThat(saved.getNextRetryTime()).isNotNull();
    }

    @Test
    void batchManualRetryRejectsNullIdsWithClearException() {
        MqRetryScheduler scheduler = new MqRetryScheduler(
                new DeadLetterHandler(new InMemoryMqFailedMessageRepository(List.of()), new MqProperties()),
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT), List.of(new RecordingSender())),
                3
        );

        assertThatThrownBy(() -> scheduler.batchManualRetry(null, "ops-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids");
    }

    private static MqFailedMessage failedMessage(String payload) {
        MqFailedMessage message = new MqFailedMessage();
        message.setId(1L);
        message.setMessageId("msg-1");
        message.setTraceId("trace-1");
        message.setParentMessageId("parent-1");
        message.setBusinessKey("ORDER-1");
        message.setMessageType("OrderCreated");
        message.setExchange("order.exchange");
        message.setRoutingKey("order.created");
        message.setQueueName("order.queue");
        message.setPayload(payload);
        message.setErrorMessage("failed");
        message.setRetryCount(0);
        message.setMaxRetry(3);
        message.setStatus(MqFailedMessage.STATUS_PENDING);
        message.setSource(MqFailedMessage.SOURCE_CONSUME_FAIL);
        message.setNextRetryTime(new Date(System.currentTimeMillis() - 1000));
        message.setCreateTime(new Date());
        message.setUpdateTime(new Date());
        return message;
    }

    private static MqProperties properties(MqProperties.Provider provider) {
        MqProperties properties = new MqProperties();
        properties.setProvider(provider);
        return properties;
    }

    private static class RecordingSender implements MqMessageSender {

        private String destination;
        private String routingKey;
        private MessageWrapper<?> wrapper;

        @Override
        public MqProperties.Provider provider() {
            return MqProperties.Provider.RABBIT;
        }

        @Override
        public <T> void send(String destination, String routingKey, MessageWrapper<T> wrapper) {
            this.destination = destination;
            this.routingKey = routingKey;
            this.wrapper = wrapper;
        }
    }

    private static class InMemoryMqFailedMessageRepository implements MqFailedMessageRepository {

        private final Map<Long, MqFailedMessage> messages = new LinkedHashMap<>();

        private InMemoryMqFailedMessageRepository(List<MqFailedMessage> initialMessages) {
            initialMessages.forEach(message -> messages.put(message.getId(), message));
        }

        @Override
        public MqFailedMessage save(MqFailedMessage message) {
            messages.put(message.getId(), message);
            return message;
        }

        @Override
        public Optional<MqFailedMessage> findById(Long id) {
            return Optional.ofNullable(messages.get(id));
        }

        @Override
        public List<MqFailedMessage> findAll() {
            return new ArrayList<>(messages.values());
        }

        @Override
        public boolean deleteById(Long id) {
            return messages.remove(id) != null;
        }

        @Override
        public int deleteProcessed() {
            int before = messages.size();
            messages.values().removeIf(message -> MqFailedMessage.STATUS_SUCCESS.equals(message.getStatus())
                    || MqFailedMessage.STATUS_EXHAUSTED.equals(message.getStatus())
                    || MqFailedMessage.STATUS_MANUAL.equals(message.getStatus()));
            return before - messages.size();
        }
    }
}
