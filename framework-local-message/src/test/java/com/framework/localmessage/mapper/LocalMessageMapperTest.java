package com.framework.localmessage.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMessageMapperTest {

    @Test
    void mapperUsesAnnotationSqlAndGeneratedKeys() throws Exception {
        assertThat(LocalMessageMapper.class).hasAnnotation(Mapper.class);

        Method insert = LocalMessageMapper.class.getMethod("insert", String.class,
                com.framework.localmessage.model.LocalMessage.class);
        assertThat(sql(insert.getAnnotation(Insert.class).value()))
                .contains("INSERT INTO ${tableName}")
                .contains("#{message.messageId}")
                .contains("#{message.traceId}")
                .contains("#{message.parentMessageId}")
                .contains("#{message.tenantId}")
                .contains("#{message.operator}")
                .contains("#{message.source}");
        assertThat(insert.getAnnotation(Options.class).useGeneratedKeys()).isTrue();
        assertThat(insert.getAnnotation(Options.class).keyProperty()).isEqualTo("message.id");

        Method update = LocalMessageMapper.class.getMethod("update", String.class,
                com.framework.localmessage.model.LocalMessage.class);
        assertThat(sql(update.getAnnotation(Update.class).value()))
                .contains("UPDATE ${tableName} SET")
                .contains("WHERE id = #{message.id}");

        Method findDueMessages = LocalMessageMapper.class.getMethod("findDueMessages", String.class,
                com.framework.localmessage.model.LocalMessageStatus.class, java.time.LocalDateTime.class, int.class);
        assertThat(sql(findDueMessages.getAnnotation(Select.class).value()))
                .contains("WHERE status = #{status}")
                .contains("next_retry_time <= #{now}")
                .contains("LIMIT #{limit}");

        Method delete = LocalMessageMapper.class.getMethod("delete", String.class, Long.class);
        assertThat(sql(delete.getAnnotation(Delete.class).value()))
                .isEqualTo("DELETE FROM ${tableName} WHERE id = #{id}");
    }

    @Test
    void defaultAutoCreateDdlMatchesPackagedMysqlScript() throws Exception {
        Method create = LocalMessageMapper.class.getMethod("createTableIfNotExists", String.class);

        assertThat(normalizeSql(sql(create.getAnnotation(Update.class).value())
                .replace("${tableName}", "framework_local_message")))
                .isEqualTo(moduleMysqlScript("db/mysql/framework_local_message.sql"));
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
