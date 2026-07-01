package com.framework.mq.deadletter;

import com.framework.mq.mapper.MqFailedMessageMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MybatisMqFailedMessageRepositoryTest {

    @Test
    void constructorRejectsNullMapperAndInvalidTableName() {
        RecordingMqFailedMessageMapper mapper = new RecordingMqFailedMessageMapper();

        assertThatThrownBy(() -> new MybatisMqFailedMessageRepository(null, "framework_mq_failed_message"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mapper");
        assertThatThrownBy(() -> new MybatisMqFailedMessageRepository(mapper, "framework-mq-failed-message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");
    }

    @Test
    void insertDelegatesAllCompensationColumnsAndRequiresGeneratedKey() {
        RecordingMqFailedMessageMapper mapper = new RecordingMqFailedMessageMapper();
        MybatisMqFailedMessageRepository repository =
                new MybatisMqFailedMessageRepository(mapper, "framework_mq_failed_message");
        Date nextRetryTime = new Date(1_800_000L);
        Date createTime = new Date(1_000_000L);
        Date updateTime = new Date(1_200_000L);
        MqFailedMessage message = message(nextRetryTime, createTime, updateTime);

        MqFailedMessage saved = repository.save(message);

        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(mapper.tableName).isEqualTo("framework_mq_failed_message");
        assertThat(mapper.insertedMessage).satisfies(inserted -> {
            assertThat(inserted.getMessageId()).isEqualTo("msg-1");
            assertThat(inserted.getTraceId()).isEqualTo("trace-1");
            assertThat(inserted.getParentMessageId()).isEqualTo("parent-msg-1");
            assertThat(inserted.getBusinessKey()).isEqualTo("ORD-1");
            assertThat(inserted.getMessageType()).isEqualTo("OrderCreated");
            assertThat(inserted.getExchange()).isEqualTo("order.exchange");
            assertThat(inserted.getRoutingKey()).isEqualTo("order.created");
            assertThat(inserted.getQueueName()).isEqualTo("order.queue");
            assertThat(inserted.getRetryCount()).isEqualTo(2);
            assertThat(inserted.getMaxRetry()).isEqualTo(5);
            assertThat(inserted.getStatus()).isEqualTo(MqFailedMessage.STATUS_PENDING);
            assertThat(inserted.getNextRetryTime()).isEqualTo(nextRetryTime);
            assertThat(inserted.getSource()).isEqualTo(MqFailedMessage.SOURCE_DEAD_LETTER);
            assertThat(inserted.getTenantId()).isEqualTo("tenant-a");
            assertThat(inserted.getOperator()).isEqualTo("ops-user");
            assertThat(inserted.getCompensateRemark()).isEqualTo("manual retry");
        });
    }

    @Test
    void insertFailsWhenMapperDoesNotReturnGeneratedKey() {
        RecordingMqFailedMessageMapper mapper = new RecordingMqFailedMessageMapper();
        mapper.generatedKey = null;
        MybatisMqFailedMessageRepository repository =
                new MybatisMqFailedMessageRepository(mapper, "framework_mq_failed_message");

        assertThatThrownBy(() -> repository.save(message(new Date(), new Date(), new Date())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mq failed message insert failed");
    }

    @Test
    void updateFindDeleteAndTerminalCleanupDelegateToMapper() {
        RecordingMqFailedMessageMapper mapper = new RecordingMqFailedMessageMapper();
        MybatisMqFailedMessageRepository repository =
                new MybatisMqFailedMessageRepository(mapper, "framework_mq_failed_message");
        MqFailedMessage message = message(new Date(1_800_000L), new Date(1_000_000L), new Date(1_200_000L));
        message.setId(9L);
        message.setStatus(MqFailedMessage.STATUS_MANUAL);

        assertThat(repository.update(message)).isTrue();
        assertThat(mapper.updatedMessage.getId()).isEqualTo(9L);
        assertThat(mapper.updatedMessage.getStatus()).isEqualTo(MqFailedMessage.STATUS_MANUAL);
        assertThat(repository.findById(7L)).containsSame(mapper.message);
        assertThat(repository.findAll()).containsExactly(mapper.message);
        assertThat(repository.deleteById(9L)).isTrue();
        assertThat(mapper.deletedId).isEqualTo(9L);
        assertThat(repository.deleteProcessed()).isEqualTo(3);
        assertThat(mapper.cleanupStatuses).containsExactly(
                MqFailedMessage.STATUS_SUCCESS,
                MqFailedMessage.STATUS_EXHAUSTED,
                MqFailedMessage.STATUS_MANUAL);

        mapper.affectedRows = 0;

        assertThat(repository.update(message)).isFalse();
        assertThat(repository.deleteById(9L)).isFalse();
    }

    private static MqFailedMessage message(Date nextRetryTime, Date createTime, Date updateTime) {
        MqFailedMessage message = new MqFailedMessage();
        message.setMessageId("msg-1");
        message.setTraceId("trace-1");
        message.setParentMessageId("parent-msg-1");
        message.setBusinessKey("ORD-1");
        message.setMessageType("OrderCreated");
        message.setExchange("order.exchange");
        message.setRoutingKey("order.created");
        message.setQueueName("order.queue");
        message.setPayload("{\"id\":1}");
        message.setErrorMessage("downstream unavailable");
        message.setErrorStack("stacktrace");
        message.setRetryCount(2);
        message.setMaxRetry(5);
        message.setStatus(MqFailedMessage.STATUS_PENDING);
        message.setNextRetryTime(nextRetryTime);
        message.setSource(MqFailedMessage.SOURCE_DEAD_LETTER);
        message.setTenantId("tenant-a");
        message.setOperator("ops-user");
        message.setCompensateRemark("manual retry");
        message.setCreateTime(createTime);
        message.setUpdateTime(updateTime);
        return message;
    }

    private static class RecordingMqFailedMessageMapper implements MqFailedMessageMapper {

        private final MqFailedMessage message = message(new Date(1_800_000L),
                new Date(1_000_000L), new Date(1_200_000L)).copy();
        private String tableName;
        private MqFailedMessage insertedMessage;
        private MqFailedMessage updatedMessage;
        private Long generatedKey = 42L;
        private int affectedRows = 1;
        private Long deletedId;
        private List<String> cleanupStatuses = List.of();

        private RecordingMqFailedMessageMapper() {
            message.setId(7L);
        }

        @Override
        public void createTableIfNotExists(String tableName) {
            this.tableName = tableName;
        }

        @Override
        public int insert(String tableName, MqFailedMessage message) {
            this.tableName = tableName;
            this.insertedMessage = message;
            message.setId(generatedKey);
            return affectedRows;
        }

        @Override
        public int update(String tableName, MqFailedMessage message) {
            this.tableName = tableName;
            this.updatedMessage = message;
            return affectedRows;
        }

        @Override
        public MqFailedMessage findById(String tableName, Long id) {
            this.tableName = tableName;
            return message.getId().equals(id) ? message : null;
        }

        @Override
        public List<MqFailedMessage> findAll(String tableName) {
            this.tableName = tableName;
            return new ArrayList<>(List.of(message));
        }

        @Override
        public List<MqFailedMessage> list(String tableName, String queueName, String status,
                                          String traceIdLike, String businessKeyLike, String messageType,
                                          int offset, int pageSize) {
            this.tableName = tableName;
            return List.of();
        }

        @Override
        public long count(String tableName, String queueName, String status,
                          String traceIdLike, String businessKeyLike, String messageType) {
            this.tableName = tableName;
            return 0;
        }

        @Override
        public long countAll(String tableName) {
            this.tableName = tableName;
            return 0;
        }

        @Override
        public long countByStatus(String tableName, String status) {
            this.tableName = tableName;
            return 0;
        }

        @Override
        public int deleteById(String tableName, Long id) {
            this.tableName = tableName;
            this.deletedId = id;
            return affectedRows;
        }

        @Override
        public int deleteProcessed(String tableName, String successStatus,
                                   String exhaustedStatus, String manualStatus) {
            this.tableName = tableName;
            this.cleanupStatuses = List.of(successStatus, exhaustedStatus, manualStatus);
            return 3;
        }
    }
}
