package com.framework.localmessage.service;

import com.framework.core.trace.TraceContext;
import com.framework.localmessage.config.LocalMessageProperties;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.repository.LocalMessageRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultLocalMessageServiceTest {

    @Test
    void publishPersistsPendingMessageWithRetryPolicy() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        LocalMessageProperties properties = properties();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties, List.of());

        LocalMessage message = service.publish("order.created", "ORD-1", "{\"id\":1}");

        assertThat(message.getId()).isNotNull();
        assertThat(message.getMessageId()).isNotBlank();
        assertThat(message.getTraceId()).isNotBlank();
        assertThat(message.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(message.getMaxRetry()).isEqualTo(properties.getMaxRetry());
        assertThat(message.getNextRetryTime()).isNotNull();
        assertThat(repository.findAll()).containsExactly(message);
    }

    @Test
    void publishPersistsMessageContextForTracingAndCompensation() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of());
        TraceContext.putTraceId("trace-001");

        try {
            LocalMessage message = service.publish(new LocalMessage()
                    .setMessageId("local-msg-1")
                    .setParentMessageId("parent-msg-1")
                    .setTopic("order.created")
                    .setBusinessKey("ORD-1")
                    .setTenantId("tenant-a")
                    .setOperator("ops-user")
                    .setSource("order-service")
                    .setPayload("{}"));

            assertThat(message.getMessageId()).isEqualTo("local-msg-1");
            assertThat(message.getTraceId()).isEqualTo("trace-001");
            assertThat(message.getParentMessageId()).isEqualTo("parent-msg-1");
            assertThat(message.getTenantId()).isEqualTo("tenant-a");
            assertThat(message.getOperator()).isEqualTo("ops-user");
            assertThat(message.getSource()).isEqualTo("order-service");
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void publishNormalizesCompensationContextBeforePersistence() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of());
        TraceContext.putTraceId("safe-trace");

        try {
            LocalMessage message = service.publish(new LocalMessage()
                    .setMessageId(" local-msg-1 ")
                    .setTraceId("bad\ntrace")
                    .setParentMessageId(" parent-msg-1 ")
                    .setTopic(" order.created ")
                    .setBusinessKey(" ORD-1 ")
                    .setTenantId(" tenant-a ")
                    .setOperator(" ops-user ")
                    .setSource(" order-service ")
                    .setPayload("{}"));

            assertThat(message.getMessageId()).isEqualTo("local-msg-1");
            assertThat(message.getTraceId()).isEqualTo("safe-trace");
            assertThat(message.getParentMessageId()).isEqualTo("parent-msg-1");
            assertThat(message.getTopic()).isEqualTo("order.created");
            assertThat(message.getBusinessKey()).isEqualTo("ORD-1");
            assertThat(message.getTenantId()).isEqualTo("tenant-a");
            assertThat(message.getOperator()).isEqualTo("ops-user");
            assertThat(message.getSource()).isEqualTo("order-service");
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void publishRejectsInvalidMessages() {
        DefaultLocalMessageService service = new DefaultLocalMessageService(
                new InMemoryLocalMessageRepository(), properties(), List.of());

        assertThatThrownBy(() -> service.publish(" ", "ORD-1", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
        assertThatThrownBy(() -> service.publish("order.created", "ORD-1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void publishNormalizesTopicBeforePersistenceAndDispatch() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        AtomicInteger handled = new AtomicInteger();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> handled.incrementAndGet()
        )));

        LocalMessage message = service.publish(" order.created ", "ORD-1", "{}");

        assertThat(message.getTopic()).isEqualTo("order.created");
        assertThat(service.retryDueMessages()).isEqualTo(1);
        assertThat(handled).hasValue(1);
        assertThat(repository.findById(message.getId()).orElseThrow().getStatus())
                .isEqualTo(LocalMessageStatus.SUCCESS);
    }

    @Test
    void constructorRejectsInvalidOrDuplicateHandlers() {
        assertThatThrownBy(() -> new DefaultLocalMessageService(
                new InMemoryLocalMessageRepository(), properties(), Arrays.asList((LocalMessageHandler) null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LocalMessageHandler must not be null");

        assertThatThrownBy(() -> new DefaultLocalMessageService(
                new InMemoryLocalMessageRepository(), properties(), List.of(handler(" ", message -> {
                }))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LocalMessageHandler topic must not be blank");

        assertThatThrownBy(() -> new DefaultLocalMessageService(
                new InMemoryLocalMessageRepository(), properties(), List.of(
                handler("order.created", message -> {
                }),
                handler(" order.created ", message -> {
                }))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate LocalMessageHandler topic");
    }

    @Test
    void constructorRejectsInvalidRetryInterval() {
        LocalMessageProperties properties = properties();
        properties.setRetryInterval(Duration.ZERO);

        assertThatThrownBy(() -> new DefaultLocalMessageService(
                new InMemoryLocalMessageRepository(), properties, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.local-message.retry-interval");
    }

    @Test
    void retryDueMessagesMarksMessageSuccessWhenHandlerCompletes() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        AtomicInteger handled = new AtomicInteger();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> handled.incrementAndGet()
        )));
        LocalMessage message = service.publish("order.created", "ORD-1", "{}");

        int count = service.retryDueMessages();

        assertThat(count).isEqualTo(1);
        assertThat(handled).hasValue(1);
        assertThat(repository.findById(message.getId()).orElseThrow().getStatus())
                .isEqualTo(LocalMessageStatus.SUCCESS);
        assertThat(repository.findById(message.getId()).orElseThrow().getNextRetryTime()).isNull();
    }

    @Test
    void retryDueMessagesRunsHandlerWithMessageTraceIdAndRestoresCallerTrace() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        AtomicReference<String> handlerTraceId = new AtomicReference<>();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> handlerTraceId.set(TraceContext.getTraceId())
        )));
        LocalMessage message = service.publish(new LocalMessage()
                .setTopic("order.created")
                .setTraceId("message-trace")
                .setBusinessKey("ORD-1")
                .setPayload("{}"));
        TraceContext.putTraceId("caller-trace");

        try {
            int count = service.retryDueMessages();

            assertThat(count).isEqualTo(1);
            assertThat(handlerTraceId).hasValue("message-trace");
            assertThat(TraceContext.getTraceId()).isEqualTo("caller-trace");
            assertThat(repository.findById(message.getId()).orElseThrow().getStatus())
                    .isEqualTo(LocalMessageStatus.SUCCESS);
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void retryDueMessagesRestoresCallerTraceWhenHandlerFails() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        AtomicReference<String> handlerTraceId = new AtomicReference<>();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> {
                    handlerTraceId.set(TraceContext.getTraceId());
                    throw new IllegalStateException("downstream unavailable");
                }
        )));
        LocalMessage message = service.publish(new LocalMessage()
                .setTopic("order.created")
                .setTraceId("message-trace")
                .setBusinessKey("ORD-1")
                .setPayload("{}"));
        TraceContext.putTraceId("caller-trace");

        try {
            int count = service.retryDueMessages();

            assertThat(count).isEqualTo(1);
            assertThat(handlerTraceId).hasValue("message-trace");
            assertThat(TraceContext.getTraceId()).isEqualTo("caller-trace");
            LocalMessage saved = repository.findById(message.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
            assertThat(saved.getErrorMessage()).isEqualTo("downstream unavailable");
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void retryDueMessagesProtectsStoredMessageFromHandlerMutationOnSuccess() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> message
                        .setId(999L)
                        .setTopic("other.topic")
                        .setPayload("changed")
                        .setRetryCount(99)
                        .setMaxRetry(1)
                        .setStatus(LocalMessageStatus.FAILED)
                        .setErrorMessage("changed")
        )));
        LocalMessage message = service.publish("order.created", "ORD-1", "{}");

        int count = service.retryDueMessages();

        assertThat(count).isEqualTo(1);
        LocalMessage saved = repository.findById(message.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.SUCCESS);
        assertThat(saved.getTopic()).isEqualTo("order.created");
        assertThat(saved.getPayload()).isEqualTo("{}");
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getMaxRetry()).isEqualTo(2);
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void retryNowDispatchesSingleMessageImmediately() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        AtomicInteger handled = new AtomicInteger();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> handled.incrementAndGet()
        )));
        LocalMessage message = service.publish("order.created", "ORD-1", "{}");
        message.setStatus(LocalMessageStatus.FAILED);
        message.setRetryCount(2);
        message.setErrorMessage("old error");
        message.setNextRetryTime(null);
        repository.save(message);

        boolean result = service.retryNow(message.getId());

        assertThat(result).isTrue();
        assertThat(handled).hasValue(1);
        LocalMessage saved = repository.findById(message.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.SUCCESS);
        assertThat(saved.getRetryCount()).isEqualTo(2);
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getNextRetryTime()).isNull();
    }

    @Test
    void retryNowRecordsMissingHandlerAsManualAttemptFailure() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        LocalMessageProperties properties = properties();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties, List.of());
        LocalMessage message = service.publish("order.created", "ORD-1", "{}");
        message.setStatus(LocalMessageStatus.FAILED);
        message.setRetryCount(1);
        message.setNextRetryTime(null);
        repository.save(message);

        boolean result = service.retryNow(message.getId());

        assertThat(result).isTrue();
        LocalMessage saved = repository.findById(message.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.FAILED);
        assertThat(saved.getRetryCount()).isEqualTo(2);
        assertThat(saved.getErrorMessage())
                .isEqualTo("No LocalMessageHandler registered for topic: order.created");
        assertThat(saved.getNextRetryTime()).isNull();
    }

    @Test
    void retryNowDoesNotMutateMessageWhenProcessingSaveFails() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        AtomicInteger handled = new AtomicInteger();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> handled.incrementAndGet()
        )));
        LocalMessage message = service.publish("order.created", "ORD-1", "{}");
        message.setStatus(LocalMessageStatus.FAILED);
        message.setRetryCount(2);
        message.setErrorMessage("old error");
        message.setNextRetryTime(null);
        repository.save(message);
        repository.failOnSave = true;

        assertThatThrownBy(() -> service.retryNow(message.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        assertThat(handled).hasValue(0);
        LocalMessage saved = repository.findById(message.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.FAILED);
        assertThat(saved.getRetryCount()).isEqualTo(2);
        assertThat(saved.getErrorMessage()).isEqualTo("old error");
        assertThat(saved.getNextRetryTime()).isNull();
    }

    @Test
    void retryNowReturnsFalseWhenMessageDoesNotExist() {
        DefaultLocalMessageService service = new DefaultLocalMessageService(
                new InMemoryLocalMessageRepository(), properties(), List.of());

        assertThat(service.retryNow(404L)).isFalse();
    }

    @Test
    void markSuccessDoesNotMutateFetchedMessageWhenSaveFails() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of());
        LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(5);
        LocalMessage message = repository.save(new LocalMessage()
                .setTopic("order.created")
                .setPayload("{}")
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(1)
                .setMaxRetry(2)
                .setNextRetryTime(nextRetryTime)
                .setErrorMessage("old error"));
        repository.failOnSave = true;

        assertThatThrownBy(() -> service.markSuccess(message.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        LocalMessage saved = repository.findById(message.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getErrorMessage()).isEqualTo("old error");
        assertThat(saved.getNextRetryTime()).isEqualTo(nextRetryTime);
    }

    @Test
    void markFailureDoesNotMutateFetchedMessageWhenSaveFails() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of());
        LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(5);
        LocalMessage message = repository.save(new LocalMessage()
                .setTopic("order.created")
                .setPayload("{}")
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(1)
                .setMaxRetry(2)
                .setNextRetryTime(nextRetryTime)
                .setErrorMessage("old error"));
        repository.failOnSave = true;

        assertThatThrownBy(() -> service.markFailure(message.getId(), new IllegalStateException("manual failure")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        LocalMessage saved = repository.findById(message.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getErrorMessage()).isEqualTo("old error");
        assertThat(saved.getNextRetryTime()).isEqualTo(nextRetryTime);
    }

    @Test
    void manualOperationsRejectInvalidIdsBeforeRepositoryLookup() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of());

        assertThatThrownBy(() -> service.retryNow(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("local message id must be greater than 0");
        assertThatThrownBy(() -> service.retryNow(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("local message id must be greater than 0");
        assertThatThrownBy(() -> service.markSuccess(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("local message id must be greater than 0");
        assertThatThrownBy(() -> service.markFailure(null, new IllegalStateException("manual failure")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("local message id must be greater than 0");
        assertThatThrownBy(() -> service.findById(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("local message id must be greater than 0");
        assertThat(repository.findByIdCalls).isZero();
    }

    @Test
    void retryDueMessagesNormalizesPersistedTopicBeforeHandlerLookup() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        AtomicInteger handled = new AtomicInteger();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> handled.incrementAndGet()
        )));
        LocalMessage message = repository.save(new LocalMessage()
                .setTopic(" order.created ")
                .setPayload("{}")
                .setStatus(LocalMessageStatus.PENDING)
                .setMaxRetry(2)
                .setNextRetryTime(LocalDateTime.now().minusSeconds(1)));

        int count = service.retryDueMessages();

        assertThat(count).isEqualTo(1);
        assertThat(handled).hasValue(1);
        assertThat(repository.findById(message.getId()).orElseThrow().getTopic()).isEqualTo("order.created");
        assertThat(repository.findById(message.getId()).orElseThrow().getStatus())
                .isEqualTo(LocalMessageStatus.SUCCESS);
    }

    @Test
    void retryDueMessagesDoesNotMutateDueMessageWhenProcessingSaveFails() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        AtomicInteger handled = new AtomicInteger();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> handled.incrementAndGet()
        )));
        LocalDateTime nextRetryTime = LocalDateTime.now().minusSeconds(1);
        LocalMessage message = repository.save(new LocalMessage()
                .setTopic("order.created")
                .setPayload("{}")
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(1)
                .setMaxRetry(2)
                .setNextRetryTime(nextRetryTime)
                .setErrorMessage("old error"));
        repository.failOnSave = true;

        assertThatThrownBy(service::retryDueMessages)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        assertThat(handled).hasValue(0);
        LocalMessage saved = repository.findById(message.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getErrorMessage()).isEqualTo("old error");
        assertThat(saved.getNextRetryTime()).isEqualTo(nextRetryTime);
    }

    @Test
    void retryDueMessagesDoesNotNormalizeTopicInPlaceWhenFailureSaveFails() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of());
        LocalMessage message = repository.save(new LocalMessage()
                .setTopic(" order.created ")
                .setPayload("{}")
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(1)
                .setMaxRetry(2)
                .setNextRetryTime(LocalDateTime.now().minusSeconds(1)));
        repository.failOnSave = true;

        assertThatThrownBy(service::retryDueMessages)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        LocalMessage saved = repository.findById(message.getId()).orElseThrow();
        assertThat(saved.getTopic()).isEqualTo(" order.created ");
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void retryDueMessagesReschedulesThenFailsWhenRetryLimitIsReached() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        LocalMessageProperties properties = properties();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties, List.of(handler(
                "order.created",
                message -> {
                    throw new IllegalStateException("downstream unavailable");
                }
        )));
        LocalMessage message = service.publish("order.created", "ORD-1", "{}");

        service.retryDueMessages();
        LocalMessage firstFailure = repository.findById(message.getId()).orElseThrow();
        assertThat(firstFailure.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(firstFailure.getRetryCount()).isEqualTo(1);
        assertThat(firstFailure.getNextRetryTime()).isNotNull();
        assertThat(firstFailure.getErrorMessage()).isEqualTo("downstream unavailable");

        firstFailure.setNextRetryTime(LocalDateTime.now().minusSeconds(1));
        service.retryDueMessages();

        LocalMessage finalFailure = repository.findById(message.getId()).orElseThrow();
        assertThat(finalFailure.getStatus()).isEqualTo(LocalMessageStatus.FAILED);
        assertThat(finalFailure.getRetryCount()).isEqualTo(properties.getMaxRetry());
        assertThat(finalFailure.getNextRetryTime()).isNull();
    }

    @Test
    void retryDueMessagesStoresExceptionClassWhenHandlerFailureMessageIsBlank() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> {
                    throw new IllegalStateException();
                }
        )));
        LocalMessage message = service.publish("order.created", "ORD-1", "{}");

        service.retryDueMessages();

        LocalMessage failure = repository.findById(message.getId()).orElseThrow();
        assertThat(failure.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(failure.getErrorMessage()).isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void retryDueMessagesProtectsRetryPolicyFromHandlerMutationOnFailure() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of(handler(
                "order.created",
                message -> {
                    message.setId(999L)
                            .setRetryCount(99)
                            .setMaxRetry(1)
                            .setStatus(LocalMessageStatus.SUCCESS);
                    throw new IllegalStateException("handler failed");
                }
        )));
        LocalMessage message = service.publish("order.created", "ORD-1", "{}");

        service.retryDueMessages();

        LocalMessage failure = repository.findById(message.getId()).orElseThrow();
        assertThat(failure.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(failure.getRetryCount()).isEqualTo(1);
        assertThat(failure.getMaxRetry()).isEqualTo(2);
        assertThat(failure.getErrorMessage()).isEqualTo("handler failed");
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void retryDueMessagesMarksMissingHandlerAsRetryableFailureThenFailed() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        LocalMessageProperties properties = properties();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties, List.of());
        LocalMessage message = service.publish("order.created", "ORD-1", "{}");

        int count = service.retryDueMessages();

        assertThat(count).isEqualTo(1);
        LocalMessage firstFailure = repository.findById(message.getId()).orElseThrow();
        assertThat(firstFailure.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(firstFailure.getRetryCount()).isEqualTo(1);
        assertThat(firstFailure.getNextRetryTime()).isNotNull();
        assertThat(firstFailure.getErrorMessage())
                .isEqualTo("No LocalMessageHandler registered for topic: order.created");

        firstFailure.setNextRetryTime(LocalDateTime.now().minusSeconds(1));
        service.retryDueMessages();

        LocalMessage finalFailure = repository.findById(message.getId()).orElseThrow();
        assertThat(finalFailure.getStatus()).isEqualTo(LocalMessageStatus.FAILED);
        assertThat(finalFailure.getRetryCount()).isEqualTo(properties.getMaxRetry());
        assertThat(finalFailure.getNextRetryTime()).isNull();
    }

    private static LocalMessageProperties properties() {
        LocalMessageProperties properties = new LocalMessageProperties();
        properties.setMaxRetry(2);
        properties.setBatchSize(10);
        properties.setRetryInterval(Duration.ofMinutes(5));
        return properties;
    }

    private static LocalMessageHandler handler(String topic, ThrowingMessageConsumer consumer) {
        return new LocalMessageHandler() {
            @Override
            public String topic() {
                return topic;
            }

            @Override
            public void handle(LocalMessage message) throws Exception {
                consumer.accept(message);
            }
        };
    }

    private interface ThrowingMessageConsumer {
        void accept(LocalMessage message) throws Exception;
    }

    private static class InMemoryLocalMessageRepository implements LocalMessageRepository {

        private final Map<Long, LocalMessage> messages = new LinkedHashMap<>();
        private long nextId = 1;
        private int findByIdCalls;
        private boolean failOnSave;

        @Override
        public LocalMessage save(LocalMessage message) {
            if (failOnSave) {
                throw new IllegalStateException("save failed");
            }
            if (message.getId() == null) {
                message.setId(nextId++);
            }
            messages.put(message.getId(), message);
            return message;
        }

        @Override
        public boolean update(LocalMessage message) {
            if (failOnSave) {
                throw new IllegalStateException("save failed");
            }
            messages.put(message.getId(), message);
            return true;
        }

        @Override
        public Optional<LocalMessage> findById(Long id) {
            findByIdCalls++;
            return Optional.ofNullable(messages.get(id));
        }

        @Override
        public List<LocalMessage> findDueMessages(LocalDateTime now, int limit) {
            return messages.values().stream()
                    .filter(message -> message.getStatus() == LocalMessageStatus.PENDING)
                    .filter(message -> message.getNextRetryTime() == null
                            || !message.getNextRetryTime().isAfter(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<LocalMessage> findAll() {
            return new ArrayList<>(messages.values());
        }

        @Override
        public boolean delete(Long id) {
            return messages.remove(id) != null;
        }
    }
}
