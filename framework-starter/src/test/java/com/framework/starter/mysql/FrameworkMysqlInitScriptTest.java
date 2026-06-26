package com.framework.starter.mysql;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkMysqlInitScriptTest {

    private static final List<String> MODULE_SCRIPT_PATHS = List.of(
            "framework-log/src/main/resources/db/mysql/sys_operation_log.sql",
            "framework-local-message/src/main/resources/db/mysql/framework_local_message.sql",
            "framework-mq/src/main/resources/db/mysql/framework_mq.sql"
    );

    @Test
    void aggregateMysqlScriptDocumentsSourcesAndContainsAllFrameworkTables() throws Exception {
        Path script = projectRoot().resolve("sql/mysql/framework_boot_starter_init.sql");

        assertThat(Files.exists(script)).isTrue();
        String sql = Files.readString(script, StandardCharsets.UTF_8);

        assertThat(sql)
                .startsWith("-- Framework Boot Starter MySQL initialization script")
                .contains("-- Source: framework-log/src/main/resources/db/mysql/sys_operation_log.sql")
                .contains("-- Source: framework-local-message/src/main/resources/db/mysql/framework_local_message.sql")
                .contains("-- Source: framework-mq/src/main/resources/db/mysql/framework_mq.sql")
                .contains("CREATE TABLE IF NOT EXISTS sys_operation_log")
                .contains("CREATE TABLE IF NOT EXISTS framework_local_message")
                .contains("CREATE TABLE IF NOT EXISTS framework_mq_failed_message")
                .contains("message_id VARCHAR(64)")
                .contains("trace_id VARCHAR(64)")
                .contains("parent_message_id VARCHAR(64)")
                .contains("tenant_id VARCHAR(64)")
                .contains("operator VARCHAR(64)")
                .contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Test
    void aggregateMysqlScriptSetsMysqlSessionDefaultsBeforeAnyDdl() throws Exception {
        String sql = aggregateSql();

        assertThat(sql).containsSubsequence(
                "SET NAMES utf8mb4;",
                "SET time_zone = '+08:00';",
                "-- Source: "
        );
    }

    @Test
    void aggregateMysqlScriptEmbedsModuleScriptsWithoutDrift() throws Exception {
        String aggregateSql = aggregateSql();

        for (String moduleScriptPath : MODULE_SCRIPT_PATHS) {
            String sourceMarker = "-- Source: " + moduleScriptPath;
            String moduleSql = Files.readString(projectRoot().resolve(moduleScriptPath), StandardCharsets.UTF_8).trim();

            assertThat(extractSourceSql(aggregateSql, sourceMarker))
                    .as(moduleScriptPath)
                    .isEqualTo(moduleSql);
        }
    }

    private static String aggregateSql() throws Exception {
        return Files.readString(projectRoot().resolve("sql/mysql/framework_boot_starter_init.sql"), StandardCharsets.UTF_8);
    }

    private static String extractSourceSql(String aggregateSql, String sourceMarker) {
        int sourceIndex = aggregateSql.indexOf(sourceMarker);
        assertThat(sourceIndex).as(sourceMarker).isNotNegative();

        int sqlStart = aggregateSql.indexOf('\n', sourceIndex);
        assertThat(sqlStart).as(sourceMarker).isNotNegative();

        int nextSource = aggregateSql.indexOf("\n-- Source: ", sqlStart + 1);
        if (nextSource < 0) {
            nextSource = aggregateSql.length();
        }
        return aggregateSql.substring(sqlStart + 1, nextSource).trim();
    }

    private static Path projectRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.exists(cwd.resolve("sql/mysql/framework_boot_starter_init.sql"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve("sql/mysql/framework_boot_starter_init.sql"))) {
            return parent;
        }
        throw new IllegalStateException("Cannot locate framework project root from " + cwd);
    }
}
