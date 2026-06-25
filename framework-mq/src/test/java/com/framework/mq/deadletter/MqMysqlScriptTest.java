package com.framework.mq.deadletter;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MqMysqlScriptTest {

    @Test
    void mysqlInitScriptDefinesMqManagementTable() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/mysql/framework_mq.sql");

        assertThat(resource.exists()).isTrue();
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS framework_mq_failed_message")
                .contains("trace_id")
                .contains("ENGINE=InnoDB")
                .contains("DEFAULT CHARSET=utf8mb4");
    }
}
