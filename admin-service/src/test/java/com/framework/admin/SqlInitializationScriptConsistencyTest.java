package com.framework.admin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlInitializationScriptConsistencyTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path AGGREGATE_SCRIPT = REPO_ROOT.resolve("sql/mysql/framework_boot_starter_init.sql");
    private static final List<Path> MODULE_SCRIPTS = List.of(
            REPO_ROOT.resolve("framework-log/src/main/resources/db/mysql/sys_operation_log.sql"),
            REPO_ROOT.resolve("framework-local-message/src/main/resources/db/mysql/framework_local_message.sql"),
            REPO_ROOT.resolve("framework-mq/src/main/resources/db/mysql/framework_mq.sql"),
            REPO_ROOT.resolve("admin-service/src/main/resources/db/mysql/admin_service.sql")
    );

    @Test
    void aggregateMysqlScriptContainsCurrentModuleScripts() throws IOException {
        String aggregateSql = normalize(Files.readString(AGGREGATE_SCRIPT));

        for (Path moduleScript : MODULE_SCRIPTS) {
            assertThat(aggregateSql)
                    .as("aggregate script should include %s", REPO_ROOT.relativize(moduleScript))
                    .contains(normalize(Files.readString(moduleScript)));
        }
    }

    private String normalize(String sql) {
        return sql.replace("\r\n", "\n")
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("--"))
                .filter(line -> !line.equalsIgnoreCase("SET NAMES utf8mb4;"))
                .filter(line -> !line.toLowerCase().startsWith("set time_zone"))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
