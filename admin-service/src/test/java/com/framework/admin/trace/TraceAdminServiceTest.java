package com.framework.admin.trace;

import com.framework.admin.trace.TraceAdminModels.TraceDetail;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.service.LocalMessageService;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import com.framework.mq.config.MqProperties;
import com.framework.mq.deadletter.DeadLetterHandler;
import com.framework.mq.deadletter.MqFailedMessage;
import com.framework.mq.deadletter.MqFailedMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class TraceAdminServiceTest {

    @Test
    void controllerRejectsBlankTraceId() {
        TraceAdminController controller = new TraceAdminController(service(List.of(), List.of(), List.of()));

        Result<TraceDetail> result = controller.detail(" ");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("traceId 不能为空");
    }

    @Test
    void aggregatesLogsMqAndLocalMessagesByTraceId() {
        Date logTime = new Date(1_000);
        Date mqTime = new Date(3_000);
        LocalDateTime localTime = LocalDateTime.now().minusMinutes(1);
        TraceAdminService service = service(
                List.of(operationLog("trace-a", false, "删除用户", "/admin/users/1", logTime)),
                List.of(
                        mqMessage(1L, "trace-a", MqFailedMessage.STATUS_EXHAUSTED, mqTime),
                        mqMessage(2L, "trace-b", MqFailedMessage.STATUS_PENDING, new Date(5_000))),
                List.of(
                        localMessage(1L, "trace-a", LocalMessageStatus.FAILED, localTime),
                        localMessage(2L, "trace-b", LocalMessageStatus.SUCCESS, LocalDateTime.now())));

        TraceDetail detail = service.detail("trace-a");

        assertThat(detail.getTraceId()).isEqualTo("trace-a");
        assertThat(detail.getSummary())
                .containsEntry("logs", 1L)
                .containsEntry("mqMessages", 1L)
                .containsEntry("localMessages", 1L)
                .containsEntry("failed", 3L);
        assertThat(detail.getLogs()).hasSize(1);
        assertThat(detail.getMqMessages()).extracting(MqFailedMessage::getId).containsExactly(1L);
        assertThat(detail.getLocalMessages()).extracting("id").containsExactly(1L);
        assertThat(detail.getTimeline())
                .extracting("source", "title", "status")
                .contains(
                        tuple("LOG", "删除用户", "FAILED"),
                        tuple("MQ", "OrderCreated", MqFailedMessage.STATUS_EXHAUSTED),
                        tuple("LOCAL_MESSAGE", "order.created", "FAILED"));
    }

    @Test
    void returnsEmptyDetailWhenOptionalRuntimesAreMissing() {
        TraceAdminService service = new TraceAdminService(provider(null), provider(null), provider(null));

        TraceDetail detail = service.detail("trace-empty");

        assertThat(detail.getSummary())
                .containsEntry("logs", 0L)
                .containsEntry("mqMessages", 0L)
                .containsEntry("localMessages", 0L)
                .containsEntry("failed", 0L);
        assertThat(detail.getTimeline()).isEmpty();
        assertThat(detail.getLogs()).isEmpty();
        assertThat(detail.getMqMessages()).isEmpty();
        assertThat(detail.getLocalMessages()).isEmpty();
    }

    private static TraceAdminService service(List<OperationLogEntity> logs,
                                             List<MqFailedMessage> mqMessages,
                                             List<LocalMessage> localMessages) {
        return new TraceAdminService(
                provider(mapper(logs)),
                provider(new DeadLetterHandler(new InMemoryMqFailedMessageRepository(mqMessages), new MqProperties())),
                provider(localMessageService(localMessages)));
    }

    private static OperationLogEntity operationLog(String traceId, boolean success, String action, String uri, Date time) {
        OperationLogEntity log = new OperationLogEntity();
        log.setTraceId(traceId);
        log.setSuccess(success);
        log.setAction(action);
        log.setUri(uri);
        log.setCreateTime(time);
        return log;
    }

    private static MqFailedMessage mqMessage(Long id, String traceId, String status, Date time) {
        MqFailedMessage message = new MqFailedMessage();
        message.setId(id);
        message.setMessageId("mq-" + id);
        message.setTraceId(traceId);
        message.setBusinessKey("order-" + id);
        message.setMessageType("OrderCreated");
        message.setQueueName("order.queue");
        message.setStatus(status);
        message.setCreateTime(time);
        return message;
    }

    private static LocalMessage localMessage(Long id, String traceId, LocalMessageStatus status, LocalDateTime time) {
        return new LocalMessage()
                .setId(id)
                .setMessageId("local-" + id)
                .setTraceId(traceId)
                .setTopic("order.created")
                .setBusinessKey("order-" + id)
                .setStatus(status)
                .setCreateTime(time);
    }

    private static OperationLogMapper mapper(List<OperationLogEntity> logs) {
        return new OperationLogMapper() {
            @Override
            public void createTableIfNotExists() {
            }

            @Override
            public void insert(OperationLogEntity entity) {
            }

            @Override
            public List<OperationLogEntity> selectList(String module, String logType, Long operatorId,
                                                       Boolean success, String traceId, int offset, int pageSize) {
                return logs.stream()
                        .filter(log -> traceId.equals(log.getTraceId()))
                        .toList();
            }

            @Override
            public long count(String module, String logType, Long operatorId, Boolean success, String traceId) {
                return logs.stream().filter(log -> traceId.equals(log.getTraceId())).count();
            }

            @Override
            public int deleteBefore(Date beforeDate) {
                return 0;
            }
        };
    }

    private static LocalMessageService localMessageService(List<LocalMessage> messages) {
        return new LocalMessageService() {
            @Override
            public LocalMessage publish(String topic, String businessKey, String payload) {
                return null;
            }

            @Override
            public int retryDueMessages() {
                return 0;
            }

            @Override
            public void markSuccess(Long id) {
            }

            @Override
            public void markFailure(Long id, Exception exception) {
            }

            @Override
            public Optional<LocalMessage> findById(Long id) {
                return messages.stream().filter(message -> id.equals(message.getId())).findFirst();
            }

            @Override
            public List<LocalMessage> findAll() {
                return messages;
            }
        };
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
