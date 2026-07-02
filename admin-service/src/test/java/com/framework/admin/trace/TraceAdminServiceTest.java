package com.framework.admin.trace;

import com.framework.admin.trace.TraceAdminModels.TraceDetail;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.localmessage.config.LocalMessageProperties;
import com.framework.localmessage.mapper.LocalMessageMapper;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import com.framework.mq.config.MqProperties;
import com.framework.mq.deadletter.MqFailedMessage;
import com.framework.mq.mapper.MqFailedMessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class TraceAdminServiceTest {

    @Test
    void controllerRejectsBlankTraceId() {
        TraceAdminController controller = new TraceAdminController(service(List.of(), List.of(), List.of()));

        Result<TraceDetail> result = controller.detail("\u00A0\u3000");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("traceId 不能为空");
    }

    @Test
    void controllerRejectsUnsafeTraceId() {
        TraceAdminController controller = new TraceAdminController(service(List.of(), List.of(), List.of()));

        Result<TraceDetail> result = controller.detail("bad\ntrace");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("traceId 不合法");
    }

    @Test
    void controllerNormalizesTraceIdBeforeQueryingDetail() {
        TraceAdminController controller = new TraceAdminController(service(List.of(), List.of(), List.of()));

        Result<TraceDetail> result = controller.detail("\u00A0trace-a\u3000");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTraceId()).isEqualTo("trace-a");
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
        assertThat(detail.getDisplayed())
                .containsEntry("logs", 1L)
                .containsEntry("mqMessages", 1L)
                .containsEntry("localMessages", 1L)
                .containsEntry("timeline", 3L);
        assertThat(detail.getTruncated())
                .containsEntry("logs", false)
                .containsEntry("mqMessages", false)
                .containsEntry("localMessages", false)
                .containsEntry("timeline", false);
        assertThat(detail.getLimit()).isEqualTo(200);
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
    void timelineMessagesAreSingleLineAndBounded() {
        OperationLogEntity log = operationLog("trace-a", false, "删除用户", "/admin/users/1", new Date(1_000));
        log.setErrorMessage("log\u00A0line\n" + "x".repeat(1500));
        MqFailedMessage mqMessage = mqMessage(1L, "trace-a", MqFailedMessage.STATUS_EXHAUSTED, new Date(2_000));
        mqMessage.setErrorMessage("mq\u3000line\n" + "y".repeat(1500));
        LocalMessage localMessage = localMessage(1L, "trace-a", LocalMessageStatus.FAILED,
                LocalDateTime.of(2026, 1, 1, 12, 0));
        localMessage.setErrorMessage("local\u00A0line\n" + "z".repeat(1500));
        TraceAdminService service = service(List.of(log), List.of(mqMessage), List.of(localMessage));

        TraceDetail detail = service.detail("trace-a");

        assertThat(detail.getTimeline()).hasSize(3);
        assertThat(detail.getTimeline())
                .extracting("message")
                .allSatisfy(message -> assertThat((String) message)
                        .hasSize(1024)
                        .doesNotContain("\n")
                        .doesNotContain("\u00A0")
                        .doesNotContain("\u3000"));
    }

    @Test
    void timelineTitlesTrimUnicodeBoundarySpaceAndSkipUnicodeBlankValues() {
        OperationLogEntity log = operationLog("trace-a", true, "\u00A0\u3000", "\u3000/admin/users\u00A0",
                new Date(1_000));
        MqFailedMessage mqMessage = mqMessage(1L, "trace-a", MqFailedMessage.STATUS_PENDING, new Date(2_000));
        mqMessage.setMessageType("\u3000OrderCreated\u00A0");
        LocalMessage localMessage = localMessage(1L, "trace-a", LocalMessageStatus.PENDING,
                LocalDateTime.of(2026, 1, 1, 12, 0));
        localMessage.setTopic("\u00A0order.created\u3000");
        TraceAdminService service = service(List.of(log), List.of(mqMessage), List.of(localMessage));

        TraceDetail detail = service.detail("trace-a");

        assertThat(detail.getTimeline())
                .extracting("source", "title")
                .contains(
                        tuple("LOG", "/admin/users"),
                        tuple("MQ", "OrderCreated"),
                        tuple("LOCAL_MESSAGE", "order.created"));
    }

    @Test
    void ordersTraceTimelineByNewestTimeWithNullsLast() {
        TraceAdminService service = service(
                List.of(
                        operationLog("trace-a", true, "旧日志", "/old", new Date(1_000)),
                        operationLog("trace-a", true, "空时间日志", "/null", null)),
                List.of(mqMessage(1L, "trace-a", MqFailedMessage.STATUS_PENDING, new Date(3_000))),
                List.of(localMessage(1L, "trace-a", LocalMessageStatus.PENDING,
                        LocalDateTime.of(2026, 1, 1, 12, 0))));

        TraceDetail detail = service.detail("trace-a");

        assertThat(detail.getTimeline())
                .extracting("source", "title")
                .containsExactly(
                        tuple("LOCAL_MESSAGE", "order.created"),
                        tuple("MQ", "OrderCreated"),
                        tuple("LOG", "旧日志"),
                        tuple("LOG", "空时间日志"));
        assertThat(detail.getTimeline().get(3).getTime()).isNull();
    }

    @Test
    void reportsTruncatedTraceDataWithTotalAndDisplayedCounts() {
        List<OperationLogEntity> logs = java.util.stream.IntStream.rangeClosed(1, 201)
                .mapToObj(index -> operationLog("trace-heavy", index % 2 == 0, "日志-" + index, "/logs/" + index,
                        new Date(index)))
                .toList();
        List<MqFailedMessage> mqMessages = java.util.stream.IntStream.rangeClosed(1, 201)
                .mapToObj(index -> mqMessage((long) index, "trace-heavy",
                        index % 2 == 0 ? MqFailedMessage.STATUS_EXHAUSTED : MqFailedMessage.STATUS_PENDING,
                        new Date(index)))
                .toList();
        List<LocalMessage> localMessages = java.util.stream.IntStream.rangeClosed(1, 201)
                .mapToObj(index -> localMessage((long) index, "trace-heavy",
                        index % 2 == 0 ? LocalMessageStatus.FAILED : LocalMessageStatus.SUCCESS,
                        LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(index)))
                .toList();
        TraceAdminService service = service(logs, mqMessages, localMessages);

        TraceDetail detail = service.detail("trace-heavy");

        assertThat(detail.getSummary())
                .containsEntry("logs", 201L)
                .containsEntry("mqMessages", 201L)
                .containsEntry("localMessages", 201L)
                .containsEntry("failed", 301L);
        assertThat(detail.getDisplayed())
                .containsEntry("logs", 200L)
                .containsEntry("mqMessages", 200L)
                .containsEntry("localMessages", 200L)
                .containsEntry("timeline", 600L);
        assertThat(detail.getTruncated())
                .containsEntry("logs", true)
                .containsEntry("mqMessages", true)
                .containsEntry("localMessages", true)
                .containsEntry("timeline", true);
        assertThat(detail.getLimit()).isEqualTo(200);
        assertThat(detail.getLogs()).hasSize(200);
        assertThat(detail.getMqMessages()).hasSize(200);
        assertThat(detail.getLocalMessages()).hasSize(200);
        assertThat(detail.getTimeline()).hasSize(600);
        assertThat(detail.getWarnings())
                .containsExactly(
                        "操作日志匹配 201 条，仅展示最新 200 条",
                        "MQ失败消息匹配 201 条，仅展示最新 200 条",
                        "本地消息匹配 201 条，仅展示最新 200 条");
    }

    @Test
    void returnsEmptyDetailWhenOptionalRuntimesAreMissing() {
        TraceAdminService service = new TraceAdminService(provider(null), provider(null), provider(null),
                provider(null), provider(null));

        TraceDetail detail = service.detail("trace-empty");

        assertThat(detail.getSummary())
                .containsEntry("logs", 0L)
                .containsEntry("mqMessages", 0L)
                .containsEntry("localMessages", 0L)
                .containsEntry("failed", 0L);
        assertThat(detail.getDisplayed())
                .containsEntry("logs", 0L)
                .containsEntry("mqMessages", 0L)
                .containsEntry("localMessages", 0L)
                .containsEntry("timeline", 0L);
        assertThat(detail.getTruncated())
                .containsEntry("logs", false)
                .containsEntry("mqMessages", false)
                .containsEntry("localMessages", false)
                .containsEntry("timeline", false);
        assertThat(detail.getTimeline()).isEmpty();
        assertThat(detail.getLogs()).isEmpty();
        assertThat(detail.getMqMessages()).isEmpty();
        assertThat(detail.getLocalMessages()).isEmpty();
        assertThat(detail.getWarnings()).isEmpty();
    }

    @Test
    void returnsEmptyDetailWhenOptionalProvidersFail() {
        TraceAdminService service = new TraceAdminService(failingProvider(), failingProvider(), provider(null),
                failingProvider(), provider(null));

        TraceDetail detail = service.detail("trace-provider-failed");

        assertThat(detail.getSummary())
                .containsEntry("logs", 0L)
                .containsEntry("mqMessages", 0L)
                .containsEntry("localMessages", 0L)
                .containsEntry("failed", 0L);
        assertThat(detail.getTimeline()).isEmpty();
        assertThat(detail.getLogs()).isEmpty();
        assertThat(detail.getMqMessages()).isEmpty();
        assertThat(detail.getLocalMessages()).isEmpty();
        assertThat(detail.getWarnings())
                .containsExactly(
                        "操作日志数据不可用: trace optional provider unavailable",
                        "MQ失败消息数据不可用: trace optional provider unavailable",
                        "本地消息数据不可用: trace optional provider unavailable");
    }

    @Test
    void returnsWarningsWhenTraceDataQueriesFail() {
        TraceAdminService service = new TraceAdminService(
                provider(failingMapper()),
                provider(failingMqFailedMessageMapper()),
                provider(mqProperties()),
                provider(failingLocalMessageMapper()),
                provider(localMessageProperties()));

        TraceDetail detail = service.detail("trace-query-failed");

        assertThat(detail.getSummary())
                .containsEntry("logs", 0L)
                .containsEntry("mqMessages", 0L)
                .containsEntry("localMessages", 0L)
                .containsEntry("failed", 0L);
        assertThat(detail.getWarnings())
                .containsExactly(
                        "操作日志数据不可用: operation log table unavailable",
                        "MQ失败消息数据不可用: mq failed message table unavailable",
                        "本地消息数据不可用: local message table unavailable");
    }

    @Test
    void warningMessagesAreSingleLineAndBounded() {
        TraceAdminService service = new TraceAdminService(
                failingProvider("provider\u00A0line\n" + "x".repeat(900)),
                provider(null),
                provider(null),
                provider(null),
                provider(null));

        TraceDetail detail = service.detail("trace-warning");

        assertThat(detail.getWarnings()).hasSize(1);
        assertThat(detail.getWarnings().get(0))
                .startsWith("操作日志数据不可用: provider line ")
                .hasSize("操作日志数据不可用: ".length() + 512)
                .doesNotContain("\n")
                .doesNotContain("\u00A0");
    }

    private static TraceAdminService service(List<OperationLogEntity> logs,
                                             List<MqFailedMessage> mqMessages,
                                             List<LocalMessage> localMessages) {
        return new TraceAdminService(
                provider(mapper(logs)),
                provider(mqFailedMessageMapper(mqMessages)),
                provider(mqProperties()),
                provider(localMessageMapper(localMessages)),
                provider(localMessageProperties()));
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
                        .filter(log -> success == null || success.equals(log.getSuccess()))
                        .skip(offset)
                        .limit(pageSize)
                        .toList();
            }

            @Override
            public long count(String module, String logType, Long operatorId, Boolean success, String traceId) {
                return logs.stream()
                        .filter(log -> traceId.equals(log.getTraceId()))
                        .filter(log -> success == null || success.equals(log.getSuccess()))
                        .count();
            }

            @Override
            public int deleteBefore(Date beforeDate) {
                return 0;
            }
        };
    }

    private static OperationLogMapper failingMapper() {
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
                throw new IllegalStateException("operation log table unavailable");
            }

            @Override
            public long count(String module, String logType, Long operatorId, Boolean success, String traceId) {
                return 0;
            }

            @Override
            public int deleteBefore(Date beforeDate) {
                return 0;
            }
        };
    }

    private static MqFailedMessageMapper mqFailedMessageMapper(List<MqFailedMessage> messages) {
        return new MqFailedMessageMapper() {
            @Override
            public void createTableIfNotExists(String tableName) {
            }

            @Override
            public int insert(String tableName, MqFailedMessage message) {
                return 1;
            }

            @Override
            public int update(String tableName, MqFailedMessage message) {
                return 1;
            }

            @Override
            public MqFailedMessage findById(String tableName, Long id) {
                return messages.stream().filter(message -> id.equals(message.getId())).findFirst().orElse(null);
            }

            @Override
            public List<MqFailedMessage> findRecent(String tableName, int limit) {
                return messages;
            }

            @Override
            public List<MqFailedMessage> list(String tableName, String queueName, String status,
                                              String traceIdLike, String businessKeyLike, String messageType,
                                              int offset, int pageSize) {
                return messages.stream()
                        .filter(message -> queueName == null || queueName.equals(message.getQueueName()))
                        .filter(message -> status == null || status.equals(message.getStatus()))
                        .filter(message -> matchesLike(message.getTraceId(), traceIdLike))
                        .filter(message -> matchesLike(message.getBusinessKey(), businessKeyLike))
                        .filter(message -> messageType == null
                                || messageType.equalsIgnoreCase(message.getMessageType()))
                        .sorted(Comparator.comparing(MqFailedMessage::getCreateTime, newestDateFirst()))
                        .skip(offset)
                        .limit(pageSize)
                        .toList();
            }

            @Override
            public long count(String tableName, String queueName, String status,
                              String traceIdLike, String businessKeyLike, String messageType) {
                return messages.stream()
                        .filter(message -> queueName == null || queueName.equals(message.getQueueName()))
                        .filter(message -> status == null || status.equals(message.getStatus()))
                        .filter(message -> matchesLike(message.getTraceId(), traceIdLike))
                        .filter(message -> matchesLike(message.getBusinessKey(), businessKeyLike))
                        .filter(message -> messageType == null
                                || messageType.equalsIgnoreCase(message.getMessageType()))
                        .count();
            }

            @Override
            public long countAll(String tableName) {
                return messages.size();
            }

            @Override
            public long countByStatus(String tableName, String status) {
                return messages.stream().filter(message -> status.equals(message.getStatus())).count();
            }

            @Override
            public int deleteById(String tableName, Long id) {
                return 0;
            }

            @Override
            public int deleteProcessed(String tableName, String successStatus,
                                       String exhaustedStatus, String manualStatus) {
                return 0;
            }
        };
    }

    private static MqFailedMessageMapper failingMqFailedMessageMapper() {
        return new MqFailedMessageMapper() {
            @Override
            public void createTableIfNotExists(String tableName) {
            }

            @Override
            public int insert(String tableName, MqFailedMessage message) {
                return 1;
            }

            @Override
            public int update(String tableName, MqFailedMessage message) {
                return 1;
            }

            @Override
            public MqFailedMessage findById(String tableName, Long id) {
                return null;
            }

            @Override
            public List<MqFailedMessage> findRecent(String tableName, int limit) {
                return List.of();
            }

            @Override
            public List<MqFailedMessage> list(String tableName, String queueName, String status,
                                              String traceIdLike, String businessKeyLike, String messageType,
                                              int offset, int pageSize) {
                throw new IllegalStateException("mq failed message table unavailable");
            }

            @Override
            public long count(String tableName, String queueName, String status,
                              String traceIdLike, String businessKeyLike, String messageType) {
                return 0;
            }

            @Override
            public long countAll(String tableName) {
                return 0;
            }

            @Override
            public long countByStatus(String tableName, String status) {
                return 0;
            }

            @Override
            public int deleteById(String tableName, Long id) {
                return 0;
            }

            @Override
            public int deleteProcessed(String tableName, String successStatus,
                                       String exhaustedStatus, String manualStatus) {
                return 0;
            }
        };
    }

    private static LocalMessageMapper localMessageMapper(List<LocalMessage> messages) {
        return new LocalMessageMapper() {
            @Override
            public void createTableIfNotExists(String tableName) {
            }

            @Override
            public int insert(String tableName, LocalMessage message) {
                return 1;
            }

            @Override
            public int update(String tableName, LocalMessage message) {
                return 1;
            }

            @Override
            public LocalMessage findById(String tableName, Long id) {
                return messages.stream().filter(message -> id.equals(message.getId())).findFirst().orElse(null);
            }

            @Override
            public List<LocalMessage> findDueMessages(String tableName, LocalMessageStatus status,
                                                      LocalDateTime now, int limit) {
                return List.of();
            }

            @Override
            public List<LocalMessage> list(String tableName, String topic, LocalMessageStatus status,
                                           String traceIdLike, String businessKeyLike, int offset, int pageSize) {
                return messages.stream()
                        .filter(message -> topic == null || topic.equals(message.getTopic()))
                        .filter(message -> status == null || status.equals(message.getStatus()))
                        .filter(message -> matchesLike(message.getTraceId(), traceIdLike))
                        .filter(message -> matchesLike(message.getBusinessKey(), businessKeyLike))
                        .sorted(Comparator.comparing(LocalMessage::getCreateTime, newestLocalFirst()))
                        .skip(offset)
                        .limit(pageSize)
                        .toList();
            }

            @Override
            public long count(String tableName, String topic, LocalMessageStatus status,
                              String traceIdLike, String businessKeyLike) {
                return messages.stream()
                        .filter(message -> topic == null || topic.equals(message.getTopic()))
                        .filter(message -> status == null || status.equals(message.getStatus()))
                        .filter(message -> matchesLike(message.getTraceId(), traceIdLike))
                        .filter(message -> matchesLike(message.getBusinessKey(), businessKeyLike))
                        .count();
            }

            @Override
            public long countAll(String tableName) {
                return messages.size();
            }

            @Override
            public long countByStatus(String tableName, LocalMessageStatus status) {
                return messages.stream().filter(message -> status.equals(message.getStatus())).count();
            }

            @Override
            public int delete(String tableName, Long id) {
                return 0;
            }

            @Override
            public int deleteByStatus(String tableName, LocalMessageStatus status) {
                return 0;
            }
        };
    }

    private static LocalMessageMapper failingLocalMessageMapper() {
        return new LocalMessageMapper() {
            @Override
            public void createTableIfNotExists(String tableName) {
            }

            @Override
            public int insert(String tableName, LocalMessage message) {
                return 1;
            }

            @Override
            public int update(String tableName, LocalMessage message) {
                return 1;
            }

            @Override
            public LocalMessage findById(String tableName, Long id) {
                return null;
            }

            @Override
            public List<LocalMessage> findDueMessages(String tableName, LocalMessageStatus status,
                                                      LocalDateTime now, int limit) {
                return List.of();
            }

            @Override
            public List<LocalMessage> list(String tableName, String topic, LocalMessageStatus status,
                                           String traceIdLike, String businessKeyLike, int offset, int pageSize) {
                throw new IllegalStateException("local message table unavailable");
            }

            @Override
            public long count(String tableName, String topic, LocalMessageStatus status,
                              String traceIdLike, String businessKeyLike) {
                return 0;
            }

            @Override
            public long countAll(String tableName) {
                return 0;
            }

            @Override
            public long countByStatus(String tableName, LocalMessageStatus status) {
                return 0;
            }

            @Override
            public int delete(String tableName, Long id) {
                return 0;
            }

            @Override
            public int deleteByStatus(String tableName, LocalMessageStatus status) {
                return 0;
            }
        };
    }

    private static Comparator<LocalDateTime> newestLocalFirst() {
        return Comparator.nullsLast(Comparator.reverseOrder());
    }

    private static Comparator<Date> newestDateFirst() {
        return Comparator.nullsLast(Comparator.reverseOrder());
    }

    private static boolean matchesLike(String value, String like) {
        if (like == null) {
            return true;
        }
        if (like.startsWith("%") || like.endsWith("%")) {
            String needle = like.replace("%", "");
            return value != null && value.contains(needle);
        }
        return like.equals(value);
    }

    private static LocalMessageProperties localMessageProperties() {
        LocalMessageProperties properties = new LocalMessageProperties();
        properties.setTableName("framework_local_message");
        return properties;
    }

    private static MqProperties mqProperties() {
        MqProperties properties = new MqProperties();
        properties.setFailedMessageTableName("framework_mq_failed_message");
        return properties;
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

    private static <T> ObjectProvider<T> failingProvider() {
        return failingProvider("trace optional provider unavailable");
    }

    private static <T> ObjectProvider<T> failingProvider(String message) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                throw new IllegalStateException(message);
            }

            @Override
            public T getIfAvailable() {
                throw new IllegalStateException(message);
            }

            @Override
            public T getIfUnique() {
                throw new IllegalStateException(message);
            }

            @Override
            public T getObject() {
                throw new IllegalStateException(message);
            }

            @Override
            public Stream<T> stream() {
                return Stream.empty();
            }
        };
    }

}
