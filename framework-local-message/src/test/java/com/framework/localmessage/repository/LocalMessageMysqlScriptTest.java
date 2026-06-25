package com.framework.localmessage.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMessageMysqlScriptTest {

    @Test
    void mysqlInitScriptDefinesLocalMessageTable() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/mysql/framework_local_message.sql");

        assertThat(resource.exists()).isTrue();
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS framework_local_message")
                .contains("ENGINE=InnoDB")
                .contains("DEFAULT CHARSET=utf8mb4");
    }
}
