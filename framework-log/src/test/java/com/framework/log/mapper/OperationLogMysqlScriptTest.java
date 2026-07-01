package com.framework.log.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Method;
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

    @Test
    void defaultAutoCreateDdlMatchesPackagedMysqlScript() throws Exception {
        Method create = OperationLogMapper.class.getMethod("createTableIfNotExists");

        assertThat(normalizeSql(sql(create.getAnnotation(Update.class).value())))
                .isEqualTo(moduleMysqlScript("db/mysql/sys_operation_log.sql"));
    }

    @Test
    void listQueryUsesExplicitProjectionColumns() throws Exception {
        Method selectList = OperationLogMapper.class.getMethod("selectList",
                String.class, String.class, Long.class, Boolean.class, String.class, int.class, int.class);

        assertThat(sql(selectList.getAnnotation(Select.class).value()))
                .contains("log_type")
                .contains("operator_name")
                .contains("trace_id")
                .doesNotContain("SELECT *");
    }

    private static String moduleMysqlScript(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (var inputStream = resource.getInputStream()) {
            return normalizeSql(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String sql(String[] lines) {
        return String.join("\n", lines).trim();
    }

    private static String normalizeSql(String sql) {
        return sql.replace("\r\n", "\n")
                .trim()
                .replaceFirst(";\\s*$", "");
    }
}
