package com.framework.mq.deadletter;

import com.framework.mq.config.MqProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqTableInitializerTest {

    @Test
    void constructorRejectsNullDependencies() {
        MqProperties properties = new MqProperties();

        assertThatThrownBy(() -> new MqTableInitializer(null, properties))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jdbcTemplate");
        assertThatThrownBy(() -> new MqTableInitializer(new CapturingJdbcTemplate(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties");
    }

    @Test
    void initSkipsSqlWhenAutoCreateTableDisabled() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        MqProperties properties = new MqProperties();
        properties.setAutoCreateTable(false);

        new MqTableInitializer(jdbcTemplate, properties).init();

        assertThat(jdbcTemplate.executedSql).isNull();
    }

    @Test
    void initExecutesMysqlDdlForConfiguredTable() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        MqProperties properties = new MqProperties();
        properties.setFailedMessageTableName("tenant_mq_failed_message");

        new MqTableInitializer(jdbcTemplate, properties).init();

        assertThat(jdbcTemplate.executedSql)
                .contains("CREATE TABLE IF NOT EXISTS tenant_mq_failed_message")
                .contains("message_id VARCHAR(64)")
                .contains("trace_id VARCHAR(64)")
                .contains("parent_message_id VARCHAR(64)")
                .contains("tenant_id VARCHAR(64)")
                .contains("operator VARCHAR(64)")
                .contains("compensate_remark VARCHAR(512)")
                .contains("INDEX idx_status_next_retry (status, next_retry_time)")
                .contains("INDEX idx_trace_id (trace_id)")
                .contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {

        private String executedSql;

        @Override
        public void execute(String sql) {
            this.executedSql = sql;
        }
    }
}
