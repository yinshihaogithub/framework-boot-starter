package com.framework.admin.mq;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.mq.config.MqProperties;
import com.framework.mq.deadletter.DeadLetterHandler;
import com.framework.mq.deadletter.MqAdminDTO;
import com.framework.mq.deadletter.MqFailedMessage;
import com.framework.mq.deadletter.MqFailedMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.StaticApplicationContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MqAdminControllerTest {

    @Test
    void returnsEmptyPageWhenMqRuntimeIsNotEnabled() {
        MqAdminController controller = new MqAdminController(
                provider(null),
                provider(null),
                provider(null),
                new StaticApplicationContext(),
                auditService());

        Result<PageResult<MqAdminDTO.MqFailedMessageVO>> result = controller.listFailedMessages(
                null, null, null, null, null, -1, 0);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRecords()).isEmpty();
        assertThat(result.getData().getPageNum()).isEqualTo(1);
        assertThat(result.getData().getPageSize()).isEqualTo(20);
    }

    @Test
    void listsFailedMessagesFromFrameworkMqStore() {
        DeadLetterHandler handler = new DeadLetterHandler(
                new InMemoryMqFailedMessageRepository(List.of(
                        failedMessage(1L, "trace-a", MqFailedMessage.STATUS_PENDING),
                        failedMessage(2L, "trace-b", MqFailedMessage.STATUS_EXHAUSTED))),
                new MqProperties());
        MqAdminController controller = new MqAdminController(
                provider(handler),
                provider(null),
                provider(null),
                new StaticApplicationContext(),
                auditService());

        Result<PageResult<MqAdminDTO.MqFailedMessageVO>> result = controller.listFailedMessages(
                null, MqFailedMessage.STATUS_PENDING, "trace-a", null, null, 1, 50);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getRecords().get(0).getId()).isEqualTo(1L);
    }

    private static MqFailedMessage failedMessage(Long id, String traceId, String status) {
        MqFailedMessage message = new MqFailedMessage();
        message.setId(id);
        message.setMessageId("msg-" + id);
        message.setTraceId(traceId);
        message.setBusinessKey("order-" + id);
        message.setMessageType("OrderCreated");
        message.setExchange("order.exchange");
        message.setRoutingKey("order.created");
        message.setQueueName("order.queue");
        message.setPayload("{}");
        message.setRetryCount(0);
        message.setMaxRetry(3);
        message.setStatus(status);
        message.setCreateTime(new Date());
        message.setUpdateTime(new Date());
        return message;
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }
        };
    }

    private static AdminAuditService auditService() {
        return new AdminAuditService(null, null) {
            @Override
            public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            }

            @Override
            public void failure(HttpServletRequest request, String module, String action, String operationType,
                                Object params, Exception exception) {
            }
        };
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
            return List.copyOf(messages.values());
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
