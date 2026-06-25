package com.framework.log.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogMysqlScriptTest {

    @Test
    void mysqlInitScriptDefinesOperationLogTable() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/mysql/sys_operation_log.sql");

        assertThat(resource.exists()).isTrue();
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS sys_operation_log")
                .contains("ENGINE=InnoDB")
                .contains("DEFAULT CHARSET=utf8mb4");
    }
}
