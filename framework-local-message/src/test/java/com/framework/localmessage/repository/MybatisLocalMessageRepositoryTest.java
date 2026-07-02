package com.framework.localmessage.repository;

import com.framework.localmessage.mapper.LocalMessageMapper;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MybatisLocalMessageRepositoryTest {

    @Test
    void constructorRejectsNullMapperAndInvalidTableName() {
        RecordingLocalMessageMapper mapper = new RecordingLocalMessageMapper();

        assertThatThrownBy(() -> new MybatisLocalMessageRepository(null, "framework_local_message"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mapper");
        assertThatThrownBy(() -> new MybatisLocalMessageRepository(mapper, "framework-local-message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");
    }

    @Test
    void insertDelegatesAllCompensationColumnsAndRequiresGeneratedKey() {
        RecordingLocalMessageMapper mapper = new RecordingLocalMessageMapper();
        MybatisLocalMessageRepository repository = new MybatisLocalMessageRepository(mapper, "framework_local_message");
        LocalDateTime nextRetryTime = LocalDateTime.of(2026, 6, 25, 10, 30);
        LocalMessage message = new LocalMessage()
                .setMessageId("local-msg-1")
                .setTraceId("trace-1")
                .setParentMessageId("parent-msg-1")
                .setTopic("order.created")
                .setBusinessKey("ORD-1")
                .setTenantId("tenant-a")
                .setOperator("ops-user")
                .setSource("order-service")
                .setPayload("{\"id\":1}")
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(1)
                .setMaxRetry(3)
                .setNextRetryTime(nextRetryTime)
                .setErrorMessage("temporary");

        LocalMessage saved = repository.save(message);

        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(mapper.tableName).isEqualTo("framework_local_message");
        assertThat(mapper.insertedMessage).satisfies(inserted -> {
            assertThat(inserted.getMessageId()).isEqualTo("local-msg-1");
            assertThat(inserted.getTraceId()).isEqualTo("trace-1");
            assertThat(inserted.getParentMessageId()).isEqualTo("parent-msg-1");
            assertThat(inserted.getTopic()).isEqualTo("order.created");
            assertThat(inserted.getBusinessKey()).isEqualTo("ORD-1");
            assertThat(inserted.getTenantId()).isEqualTo("tenant-a");
            assertThat(inserted.getOperator()).isEqualTo("ops-user");
            assertThat(inserted.getSource()).isEqualTo("order-service");
            assertThat(inserted.getRetryCount()).isEqualTo(1);
            assertThat(inserted.getMaxRetry()).isEqualTo(3);
            assertThat(inserted.getNextRetryTime()).isEqualTo(nextRetryTime);
            assertThat(inserted.getCreateTime()).isNotNull();
            assertThat(inserted.getUpdateTime()).isNotNull();
        });
    }

    @Test
    void insertFailsWhenMapperDoesNotReturnGeneratedKey() {
        RecordingLocalMessageMapper mapper = new RecordingLocalMessageMapper();
        mapper.generatedKey = null;
        MybatisLocalMessageRepository repository = new MybatisLocalMessageRepository(mapper, "framework_local_message");

        assertThatThrownBy(() -> repository.save(messageWithoutId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local message insert failed");
    }

    @Test
    void updateFindDeleteAndDueQueriesDelegateToMapper() {
        RecordingLocalMessageMapper mapper = new RecordingLocalMessageMapper();
        MybatisLocalMessageRepository repository = new MybatisLocalMessageRepository(mapper, "framework_local_message");
        LocalMessage message = messageWithoutId().setId(9L).setStatus(LocalMessageStatus.SUCCESS);
        LocalDateTime now = LocalDateTime.of(2026, 6, 25, 11, 0);

        assertThat(repository.update(message)).isTrue();
        assertThat(mapper.updatedMessage.getId()).isEqualTo(9L);
        assertThat(mapper.updatedMessage.getUpdateTime()).isNotNull();

        assertThat(repository.findById(7L)).containsSame(mapper.message);
        assertThat(repository.findDueMessages(now, 10)).containsExactly(mapper.message);
        assertThat(mapper.dueStatus).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(mapper.dueNow).isEqualTo(now);
        assertThat(mapper.dueLimit).isEqualTo(10);
        assertThat(repository.delete(9L)).isTrue();
        assertThat(mapper.deletedId).isEqualTo(9L);

        mapper.affectedRows = 0;

        assertThat(repository.update(message)).isFalse();
        assertThat(repository.delete(9L)).isFalse();
    }

    private static LocalMessage messageWithoutId() {
        return new LocalMessage()
                .setMessageId("local-msg-9")
                .setTraceId("trace-9")
                .setTopic("order.created")
                .setBusinessKey("ORD-9")
                .setPayload("{\"id\":9}")
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(0)
                .setMaxRetry(3);
    }

    private static class RecordingLocalMessageMapper implements LocalMessageMapper {

        private final LocalMessage message = messageWithoutId().setId(7L);
        private String tableName;
        private LocalMessage insertedMessage;
        private LocalMessage updatedMessage;
        private Long generatedKey = 42L;
        private int affectedRows = 1;
        private LocalMessageStatus dueStatus;
        private LocalDateTime dueNow;
        private int dueLimit;
        private Long deletedId;

        @Override
        public void createTableIfNotExists(String tableName) {
            this.tableName = tableName;
        }

        @Override
        public int insert(String tableName, LocalMessage message) {
            this.tableName = tableName;
            this.insertedMessage = message;
            message.setId(generatedKey);
            return affectedRows;
        }

        @Override
        public int update(String tableName, LocalMessage message) {
            this.tableName = tableName;
            this.updatedMessage = message;
            return affectedRows;
        }

        @Override
        public LocalMessage findById(String tableName, Long id) {
            this.tableName = tableName;
            return message.getId().equals(id) ? message : null;
        }

        @Override
        public List<LocalMessage> findDueMessages(String tableName, LocalMessageStatus status,
                                                  LocalDateTime now, int limit) {
            this.tableName = tableName;
            this.dueStatus = status;
            this.dueNow = now;
            this.dueLimit = limit;
            return new ArrayList<>(List.of(message));
        }

        @Override
        public List<LocalMessage> list(String tableName, String topic, LocalMessageStatus status,
                                       String traceIdLike, String businessKeyLike, int offset, int pageSize) {
            this.tableName = tableName;
            return new ArrayList<>(List.of(message));
        }

        @Override
        public long count(String tableName, String topic, LocalMessageStatus status,
                          String traceIdLike, String businessKeyLike) {
            this.tableName = tableName;
            return 1;
        }

        @Override
        public long countAll(String tableName) {
            this.tableName = tableName;
            return 1;
        }

        @Override
        public long countByStatus(String tableName, LocalMessageStatus status) {
            this.tableName = tableName;
            return LocalMessageStatus.PENDING == status ? 1 : 0;
        }

        @Override
        public int delete(String tableName, Long id) {
            this.tableName = tableName;
            this.deletedId = id;
            return affectedRows;
        }

        @Override
        public int deleteByStatus(String tableName, LocalMessageStatus status) {
            this.tableName = tableName;
            return 0;
        }
    }
}
