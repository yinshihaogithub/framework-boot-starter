package com.framework.localmessage.repository;

import com.framework.localmessage.config.LocalMessageProperties;
import com.framework.localmessage.mapper.LocalMessageMapper;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMessageTableInitializerTest {

    @Test
    void constructorRejectsNullDependencies() {
        LocalMessageProperties properties = new LocalMessageProperties();

        assertThatThrownBy(() -> new LocalMessageTableInitializer(null, properties))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mapper");
        assertThatThrownBy(() -> new LocalMessageTableInitializer(new CapturingLocalMessageMapper(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties");
    }

    @Test
    void initSkipsSqlWhenAutoCreateTableDisabled() {
        CapturingLocalMessageMapper mapper = new CapturingLocalMessageMapper();
        LocalMessageProperties properties = new LocalMessageProperties();
        properties.setAutoCreateTable(false);

        new LocalMessageTableInitializer(mapper, properties).init();

        assertThat(mapper.createdTableName).isNull();
    }

    @Test
    void initCreatesConfiguredTableThroughMapper() {
        CapturingLocalMessageMapper mapper = new CapturingLocalMessageMapper();
        LocalMessageProperties properties = new LocalMessageProperties();
        properties.setTableName("tenant_local_message");

        new LocalMessageTableInitializer(mapper, properties).init();

        assertThat(mapper.createdTableName).isEqualTo("tenant_local_message");
    }

    @Test
    void initRejectsUnsafeTableNameBeforeMapperCall() {
        CapturingLocalMessageMapper mapper = new CapturingLocalMessageMapper();
        LocalMessageProperties properties = new LocalMessageProperties();
        properties.setTableName("framework-local-message");

        assertThatThrownBy(() -> new LocalMessageTableInitializer(mapper, properties).init())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");

        assertThat(mapper.createdTableName).isNull();
    }

    private static class CapturingLocalMessageMapper implements LocalMessageMapper {

        private String createdTableName;

        @Override
        public void createTableIfNotExists(String tableName) {
            this.createdTableName = tableName;
        }

        @Override
        public int insert(String tableName, LocalMessage message) {
            return 0;
        }

        @Override
        public int update(String tableName, LocalMessage message) {
            return 0;
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
            return List.of();
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
    }
}
