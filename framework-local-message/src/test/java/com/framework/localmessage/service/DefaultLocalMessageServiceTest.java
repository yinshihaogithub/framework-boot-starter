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
    void retryDueMessagesSkipsMessagesWithoutHandler() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        DefaultLocalMessageService service = new DefaultLocalMessageService(repository, properties(), List.of());
        LocalMessage message = service.publish("order.created", "ORD-1", "{}");

        int count = service.retryDueMessages();

        assertThat(count).isZero();
        assertThat(repository.findById(message.getId()).orElseThrow().getStatus())
                .isEqualTo(LocalMessageStatus.PENDING);
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

        @Override
        public LocalMessage save(LocalMessage message) {
            if (message.getId() == null) {
                message.setId(nextId++);
            }
            messages.put(message.getId(), message);
            return message;
        }

        @Override
        public Optional<LocalMessage> findById(Long id) {
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
        public void delete(Long id) {
            messages.remove(id);
        }
    }
}
