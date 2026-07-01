package com.framework.mq.deadletter;

import com.framework.mq.config.MqProperties;
import com.framework.mq.mapper.MqFailedMessageMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqTableInitializerTest {

    @Test
    void constructorRejectsNullDependencies() {
        MqProperties properties = new MqProperties();

        assertThatThrownBy(() -> new MqTableInitializer(null, properties))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mapper");
        assertThatThrownBy(() -> new MqTableInitializer(new CapturingMqFailedMessageMapper(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties");
    }

    @Test
    void initSkipsSqlWhenAutoCreateTableDisabled() {
        CapturingMqFailedMessageMapper mapper = new CapturingMqFailedMessageMapper();
        MqProperties properties = new MqProperties();
        properties.setAutoCreateTable(false);

        new MqTableInitializer(mapper, properties).init();

        assertThat(mapper.createdTableName).isNull();
    }

    @Test
    void initCreatesConfiguredTableThroughMapper() {
        CapturingMqFailedMessageMapper mapper = new CapturingMqFailedMessageMapper();
        MqProperties properties = new MqProperties();
        properties.setFailedMessageTableName("tenant_mq_failed_message");

        new MqTableInitializer(mapper, properties).init();

        assertThat(mapper.createdTableName).isEqualTo("tenant_mq_failed_message");
    }

    @Test
    void initRejectsUnsafeTableNameBeforeMapperCall() {
        CapturingMqFailedMessageMapper mapper = new CapturingMqFailedMessageMapper();
        MqProperties properties = new MqProperties();
        properties.setFailedMessageTableName("framework-mq-failed-message");

        assertThatThrownBy(() -> new MqTableInitializer(mapper, properties).init())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");

        assertThat(mapper.createdTableName).isNull();
    }

    private static class CapturingMqFailedMessageMapper implements MqFailedMessageMapper {

        private String createdTableName;

        @Override
        public void createTableIfNotExists(String tableName) {
            this.createdTableName = tableName;
        }

        @Override
        public int insert(String tableName, MqFailedMessage message) {
            return 0;
        }

        @Override
        public int update(String tableName, MqFailedMessage message) {
            return 0;
        }

        @Override
        public MqFailedMessage findById(String tableName, Long id) {
            return null;
        }

        @Override
        public List<MqFailedMessage> findAll(String tableName) {
            return List.of();
        }

        @Override
        public List<MqFailedMessage> list(String tableName, String queueName, String status,
                                          String traceIdLike, String businessKeyLike, String messageType,
                                          int offset, int pageSize) {
            return List.of();
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
    }
}
