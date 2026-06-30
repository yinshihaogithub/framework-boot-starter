package com.framework.localmessage.repository;

import com.framework.localmessage.config.LocalMessageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMessageTableInitializerTest {

    @Test
    void constructorRejectsNullDependencies() {
        LocalMessageProperties properties = new LocalMessageProperties();

        assertThatThrownBy(() -> new LocalMessageTableInitializer(null, properties))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jdbcTemplate");
        assertThatThrownBy(() -> new LocalMessageTableInitializer(new CapturingJdbcTemplate(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties");
    }

    @Test
    void initSkipsSqlWhenAutoCreateTableDisabled() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        LocalMessageProperties properties = new LocalMessageProperties();
        properties.setAutoCreateTable(false);

        new LocalMessageTableInitializer(jdbcTemplate, properties).init();

        assertThat(jdbcTemplate.executedSql).isNull();
    }

    @Test
    void initExecutesMysqlDdlForConfiguredTable() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        LocalMessageProperties properties = new LocalMessageProperties();
        properties.setTableName("tenant_local_message");

        new LocalMessageTableInitializer(jdbcTemplate, properties).init();

        assertThat(jdbcTemplate.executedSql)
                .contains("CREATE TABLE IF NOT EXISTS tenant_local_message")
                .contains("message_id VARCHAR(64)")
                .contains("trace_id VARCHAR(64)")
                .contains("parent_message_id VARCHAR(64)")
                .contains("tenant_id VARCHAR(64)")
                .contains("INDEX idx_status_next_retry (status, next_retry_time)")
                .contains("INDEX idx_trace_id (trace_id)")
                .contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Test
    void defaultAutoCreateDdlMatchesPackagedMysqlScript() throws Exception {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        LocalMessageProperties properties = new LocalMessageProperties();

        new LocalMessageTableInitializer(jdbcTemplate, properties).init();

        assertThat(normalizeSql(jdbcTemplate.executedSql))
                .isEqualTo(moduleMysqlScript("db/mysql/framework_local_message.sql"));
    }

    private static String moduleMysqlScript(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (var inputStream = resource.getInputStream()) {
            return normalizeSql(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String normalizeSql(String sql) {
        return sql.replace("\r\n", "\n")
                .trim()
                .replaceFirst(";\\s*$", "");
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {

        private String executedSql;

        @Override
        public void execute(String sql) {
            this.executedSql = sql;
        }
    }
}
