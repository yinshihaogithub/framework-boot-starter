package com.framework.mq.mapper;

import com.framework.mq.deadletter.MqFailedMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MqFailedMessageMapperTest {

    @Test
    void mapperUsesAnnotationSqlAndGeneratedKeys() throws Exception {
        assertThat(MqFailedMessageMapper.class).hasAnnotation(Mapper.class);

        Method insert = MqFailedMessageMapper.class.getMethod("insert", String.class, MqFailedMessage.class);
        assertThat(sql(insert.getAnnotation(Insert.class).value()))
                .contains("INSERT INTO ${tableName}")
                .contains("#{message.messageId}")
                .contains("#{message.traceId}")
                .contains("#{message.parentMessageId}")
                .contains("#{message.businessKey}")
                .contains("#{message.messageType}")
                .contains("#{message.tenantId}")
                .contains("#{message.operator}")
                .contains("#{message.compensateRemark}");
        assertThat(insert.getAnnotation(Options.class).useGeneratedKeys()).isTrue();
        assertThat(insert.getAnnotation(Options.class).keyProperty()).isEqualTo("message.id");

        Method update = MqFailedMessageMapper.class.getMethod("update", String.class, MqFailedMessage.class);
        assertThat(sql(update.getAnnotation(Update.class).value()))
                .contains("UPDATE ${tableName} SET")
                .contains("WHERE id = #{message.id}");

        Method cleanup = MqFailedMessageMapper.class.getMethod("deleteProcessed",
                String.class, String.class, String.class, String.class);
        assertThat(sql(cleanup.getAnnotation(Delete.class).value()))
                .contains("DELETE FROM ${tableName}")
                .contains("WHERE status IN (#{successStatus}, #{exhaustedStatus}, #{manualStatus})");
    }

    @Test
    void defaultAutoCreateDdlMatchesPackagedMysqlScript() throws Exception {
        Method create = MqFailedMessageMapper.class.getMethod("createTableIfNotExists", String.class);

        assertThat(normalizeSql(sql(create.getAnnotation(Update.class).value())
                .replace("${tableName}", "framework_mq_failed_message")))
                .isEqualTo(moduleMysqlScript("db/mysql/framework_mq.sql"));
    }

    private static String moduleMysqlScript(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (var inputStream = resource.getInputStream()) {
            return normalizeSql(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String sql(String[] lines) {
        return String.join("\n", lines)
                .replace("<script>", "")
                .replace("</script>", "")
                .trim();
    }

    private static String normalizeSql(String sql) {
        return sql.replace("\r\n", "\n")
                .trim()
                .replaceFirst(";\\s*$", "");
    }
}
