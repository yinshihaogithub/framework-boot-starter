package com.framework.mq.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.mq.config.MqProperties;
import com.framework.mq.core.MessageWrapper;
import com.framework.mq.producer.MqMessageSender;
import com.framework.mq.producer.MqMessageSenderRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
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
    void constructorRejectsInvalidDependenciesAndRetryLimit() {
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(
                new InMemoryMqFailedMessageRepository(List.of()), new MqProperties());
        MqMessageSenderRegistry senderRegistry = new MqMessageSenderRegistry(
                properties(MqProperties.Provider.RABBIT), List.of(new RecordingSender()));

        assertThatThrownBy(() -> new MqRetryScheduler(null, senderRegistry, 3))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("deadLetterHandler");
        assertThatThrownBy(() -> new MqRetryScheduler(deadLetterHandler, null, 3))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("senderRegistry");
        assertThatThrownBy(() -> new MqRetryScheduler(deadLetterHandler, senderRegistry, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxRetry must be greater than 0");
    }

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

        boolean result = scheduler.manualRetry(1L, "\u00A0ops-user\u3000", "\u3000fixed inventory\u00A0");

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
        assertThat(saved.getNextRetryTime()).isNull();
    }

    @Test
    void manualRetryKeepsOriginalMessageWhenPersistingSuccessFails() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        failedMessage.setStatus(MqFailedMessage.STATUS_EXHAUSTED);
        failedMessage.setRetryCount(2);
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT), List.of(new RecordingSender())),
                3
        );
        repository.failOnSave = true;

        assertThatThrownBy(() -> scheduler.manualRetry(1L, "ops-user", "retry now"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        MqFailedMessage stored = deadLetterHandler.getById(1L);
        assertThat(stored.getStatus()).isEqualTo(MqFailedMessage.STATUS_EXHAUSTED);
        assertThat(stored.getRetryCount()).isEqualTo(2);
        assertThat(stored.getOperator()).isNull();
        assertThat(stored.getCompensateRemark()).isNull();
        assertThat(stored.getErrorMessage()).isEqualTo("failed");
    }

    @Test
    void manualRetrySkipsSendingWhenRetryingStateUpdateMisses() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        RecordingSender sender = new RecordingSender();
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT), List.of(sender)),
                3
        );
        repository.updateAffected = false;

        boolean result = scheduler.manualRetry(1L, "ops-user", "retry now");

        assertThat(result).isFalse();
        assertThat(sender.wrapper).isNull();
        assertThat(deadLetterHandler.getById(1L)).isNotNull();
        MqFailedMessage stored = repository.findById(1L).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(MqFailedMessage.STATUS_PENDING);
        assertThat(stored.getRetryCount()).isZero();
    }

    @Test
    void manualRetryRejectsInvalidIdBeforeLookup() {
        MqRetryScheduler scheduler = new MqRetryScheduler(
                new DeadLetterHandler(new InMemoryMqFailedMessageRepository(List.of()), new MqProperties()),
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT), List.of(new RecordingSender())),
                3
        );

        assertThatThrownBy(() -> scheduler.manualRetry(null, "ops-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failed message id must be greater than 0");
        assertThatThrownBy(() -> scheduler.manualRetry(0L, "ops-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failed message id must be greater than 0");
    }

    @Test
    void manualRetryFailurePersistsAttemptAndSchedulesNextRetry() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        failedMessage.setRetryCount(1);
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT),
                        List.of(new FailingSender("broker\u00A0unavailable\nretry later"))),
                3
        );

        boolean result = scheduler.manualRetry(1L, "\u00A0ops-user\u3000", "\u3000retry now\u00A0");

        assertThat(result).isFalse();
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MqFailedMessage.STATUS_PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(2);
        assertThat(saved.getOperator()).isEqualTo("ops-user");
        assertThat(saved.getCompensateRemark()).isEqualTo("retry now");
        assertThat(saved.getErrorMessage())
                .contains("failed", "手动重发失败: broker unavailable retry later")
                .doesNotContain("\n")
                .doesNotContain("\u00A0");
        assertThat(saved.getNextRetryTime()).isNotNull();
    }

    @Test
    void manualRetryKeepsOriginalMessageWhenPersistingFailureFails() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        failedMessage.setRetryCount(1);
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT),
                        List.of(new FailingSender("broker unavailable"))),
                3
        );
        repository.failOnSave = true;

        assertThatThrownBy(() -> scheduler.manualRetry(1L, "ops-user", "retry now"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        MqFailedMessage stored = deadLetterHandler.getById(1L);
        assertThat(stored.getStatus()).isEqualTo(MqFailedMessage.STATUS_PENDING);
        assertThat(stored.getRetryCount()).isEqualTo(1);
        assertThat(stored.getOperator()).isNull();
        assertThat(stored.getCompensateRemark()).isNull();
        assertThat(stored.getErrorMessage()).isEqualTo("failed");
    }

    @Test
    void manualRetryFailureUsesExceptionClassNameWhenMessageIsEmpty() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT),
                        List.of(new EmptyMessageFailingSender())),
                3
        );

        boolean result = scheduler.manualRetry(1L, "ops-user", "retry now");

        assertThat(result).isFalse();
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getErrorMessage()).contains("手动重发失败: IllegalStateException");
        assertThat(saved.getErrorMessage()).doesNotContain("null");
    }

    @Test
    void manualRetryFailureUsesExceptionClassNameWhenMessageIsBlank() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT),
                        List.of(new FailingSender("\u00A0\u3000"))),
                3
        );

        boolean result = scheduler.manualRetry(1L, "ops-user", "retry now");

        assertThat(result).isFalse();
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getErrorMessage()).contains("手动重发失败: IllegalStateException");
        assertThat(saved.getErrorMessage()).doesNotContain("null");
    }

    @Test
    void manualRetryFailureMarksExhaustedWhenRetryLimitReached() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        failedMessage.setRetryCount(2);
        failedMessage.setMaxRetry(3);
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT),
                        List.of(new FailingSender("broker unavailable"))),
                3
        );

        boolean result = scheduler.manualRetry(1L, "ops-user", " ");

        assertThat(result).isFalse();
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MqFailedMessage.STATUS_EXHAUSTED);
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getCompensateRemark()).isEqualTo("手动重发失败");
        assertThat(saved.getNextRetryTime()).isNull();
    }

    @Test
    void manualRetryWrapsLegacyRawPayloadWithFailedMessageMetadata() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        failedMessage.setMessageId("\u00A0legacy-msg\u3000");
        failedMessage.setTraceId("\u3000legacy-trace\u00A0");
        failedMessage.setParentMessageId("\u00A0legacy-parent\u3000");
        failedMessage.setBusinessKey("\u00A0ORDER-2\u3000");
        failedMessage.setMessageType("\u3000LegacyEvent\u00A0");
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
        assertThat(sender.wrapper.getParentMessageId()).isEqualTo("legacy-parent");
        assertThat(sender.wrapper.getBusinessKey()).isEqualTo("ORDER-2");
        assertThat(sender.wrapper.getType()).isEqualTo("LegacyEvent");
        assertThat(sender.wrapper.getPayload()).isEqualTo("{\"legacy\":true}");
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getMessageId()).isEqualTo("legacy-msg");
        assertThat(saved.getTraceId()).isEqualTo("legacy-trace");
        assertThat(saved.getParentMessageId()).isEqualTo("legacy-parent");
        assertThat(saved.getBusinessKey()).isEqualTo("ORDER-2");
        assertThat(saved.getMessageType()).isEqualTo("LegacyEvent");
    }

    @Test
    void manualRetryFillsUnicodeBlankWrapperMetadataFromFailedRecord() throws Exception {
        MessageWrapper<String> original = MessageWrapper.of("ORDER-1", "OrderCreated", "payload");
        original.setMessageId("\u00A0\u3000");
        original.setTraceId("\u3000");
        original.setParentMessageId("\u00A0");
        original.setBusinessKey("\u3000");
        original.setType("\u00A0\u3000");
        MqFailedMessage failedMessage = failedMessage(objectMapper.writeValueAsString(original));
        failedMessage.setMessageId("\u00A0record-msg\u3000");
        failedMessage.setTraceId("\u00A0record-trace\u3000");
        failedMessage.setParentMessageId("\u3000record-parent\u00A0");
        failedMessage.setBusinessKey("\u00A0ORDER-3\u3000");
        failedMessage.setMessageType("\u3000RecordEvent\u00A0");
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
        assertThat(sender.wrapper.getMessageId()).isEqualTo("record-msg");
        assertThat(sender.wrapper.getTraceId()).isEqualTo("record-trace");
        assertThat(sender.wrapper.getParentMessageId()).isEqualTo("record-parent");
        assertThat(sender.wrapper.getBusinessKey()).isEqualTo("ORDER-3");
        assertThat(sender.wrapper.getType()).isEqualTo("RecordEvent");
        assertThat(sender.wrapper.getPayload()).isEqualTo("payload");
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getMessageId()).isEqualTo("record-msg");
        assertThat(saved.getTraceId()).isEqualTo("record-trace");
        assertThat(saved.getParentMessageId()).isEqualTo("record-parent");
        assertThat(saved.getBusinessKey()).isEqualTo("ORDER-3");
        assertThat(saved.getMessageType()).isEqualTo("RecordEvent");
    }

    @Test
    void manualRetryUsesLegacyTypeWhenFailedRecordMissesMessageType() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        failedMessage.setMessageType("\u00A0\u3000");
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
        assertThat(repository.findById(1L).orElseThrow().getMessageType()).isEqualTo("LegacyMessage");
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
    void scanAndRetrySkipsSendingWhenPersistingRetryStateFails() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        failedMessage.setRetryCount(1);
        Date nextRetryTime = new Date(System.currentTimeMillis() - 1000);
        failedMessage.setNextRetryTime(nextRetryTime);
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        RecordingSender sender = new RecordingSender();
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT), List.of(sender)),
                3
        );
        repository.failOnSave = true;

        assertThatThrownBy(scheduler::scanAndRetry)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        assertThat(sender.wrapper).isNull();
        MqFailedMessage stored = deadLetterHandler.getById(1L);
        assertThat(stored.getStatus()).isEqualTo(MqFailedMessage.STATUS_PENDING);
        assertThat(stored.getRetryCount()).isEqualTo(1);
        assertThat(stored.getErrorMessage()).isEqualTo("failed");
        assertThat(stored.getNextRetryTime()).isEqualTo(nextRetryTime);
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

    @Test
    void batchManualRetryRecordsInvalidIdsAndContinuesValidMessages() {
        MqFailedMessage failedMessage = failedMessage("{\"legacy\":true}");
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(failedMessage));
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(repository, new MqProperties());
        RecordingSender sender = new RecordingSender();
        MqRetryScheduler scheduler = new MqRetryScheduler(
                deadLetterHandler,
                new MqMessageSenderRegistry(properties(MqProperties.Provider.RABBIT), List.of(sender)),
                3
        );

        MqAdminDTO.ManualRetryResult result = scheduler.batchManualRetry(Arrays.asList(null, 0L, 1L, 404L), "ops-user");

        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(3);
        assertThat(result.getFailedMessages())
                .containsExactly("ID=null 无效", "ID=0 无效", "ID=404 重发失败");
        assertThat(repository.findById(1L).orElseThrow().getStatus()).isEqualTo(MqFailedMessage.STATUS_MANUAL);
        assertThat(sender.wrapper).isNotNull();
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

    private static class FailingSender implements MqMessageSender {

        private final String message;

        private FailingSender(String message) {
            this.message = message;
        }

        @Override
        public MqProperties.Provider provider() {
            return MqProperties.Provider.RABBIT;
        }

        @Override
        public <T> void send(String destination, String routingKey, MessageWrapper<T> wrapper) {
            throw new IllegalStateException(message);
        }
    }

    private static class EmptyMessageFailingSender implements MqMessageSender {

        @Override
        public MqProperties.Provider provider() {
            return MqProperties.Provider.RABBIT;
        }

        @Override
        public <T> void send(String destination, String routingKey, MessageWrapper<T> wrapper) {
            throw new IllegalStateException();
        }
    }

    private static class InMemoryMqFailedMessageRepository implements MqFailedMessageRepository {

        private final Map<Long, MqFailedMessage> messages = new LinkedHashMap<>();
        private boolean failOnSave;
        private boolean updateAffected = true;

        private InMemoryMqFailedMessageRepository(List<MqFailedMessage> initialMessages) {
            initialMessages.forEach(message -> messages.put(message.getId(), message));
        }

        @Override
        public MqFailedMessage save(MqFailedMessage message) {
            if (failOnSave) {
                throw new IllegalStateException("save failed");
            }
            messages.put(message.getId(), message);
            return message;
        }

        @Override
        public boolean update(MqFailedMessage message) {
            if (failOnSave) {
                throw new IllegalStateException("save failed");
            }
            if (!updateAffected) {
                return false;
            }
            messages.put(message.getId(), message);
            return true;
        }

        @Override
        public Optional<MqFailedMessage> findById(Long id) {
            return Optional.ofNullable(messages.get(id));
        }

        @Override
        public List<MqFailedMessage> findRecent(int limit) {
            return new ArrayList<>(messages.values()).stream()
                    .limit(limit)
                    .toList();
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
