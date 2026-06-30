package com.framework.localmessage.repository;

import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcLocalMessageRepositoryTest {

    @Test
    void constructorRejectsNullJdbcTemplate() {
        assertThatThrownBy(() -> new JdbcLocalMessageRepository(null, "framework_local_message"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jdbcTemplate");
    }

    @Test
    void insertBindsAllCompensationColumnsAndGeneratedKey() throws Exception {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcLocalMessageRepository repository = new JdbcLocalMessageRepository(jdbcTemplate, "framework_local_message");
        LocalDateTime nextRetryTime = LocalDateTime.of(2026, 6, 25, 10, 30);
        LocalMessage message = new LocalMessage()
                .setMessageId("local-msg-1")
                .setTraceId("trace-1")
                .setParentMessageId("parent-msg-1")
                .setTopic("order.created")
                .setBusinessKey("ORD-1")
                .setTenantId("tenant-a")
                .setOperator("ops-user")
                .setSource("order-service")
                .setPayload("{\"id\":1}")
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(1)
                .setMaxRetry(3)
                .setNextRetryTime(nextRetryTime)
                .setErrorMessage("temporary");

        LocalMessage saved = repository.save(message);

        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(jdbcTemplate.preparedSql).contains("INSERT INTO framework_local_message");
        assertThat(jdbcTemplate.preparedStatement.value(1)).isEqualTo("local-msg-1");
        assertThat(jdbcTemplate.preparedStatement.value(2)).isEqualTo("trace-1");
        assertThat(jdbcTemplate.preparedStatement.value(3)).isEqualTo("parent-msg-1");
        assertThat(jdbcTemplate.preparedStatement.value(4)).isEqualTo("order.created");
        assertThat(jdbcTemplate.preparedStatement.value(6)).isEqualTo("tenant-a");
        assertThat(jdbcTemplate.preparedStatement.value(7)).isEqualTo("ops-user");
        assertThat(jdbcTemplate.preparedStatement.value(8)).isEqualTo("order-service");
        assertThat(jdbcTemplate.preparedStatement.value(11)).isEqualTo(1);
        assertThat(jdbcTemplate.preparedStatement.value(12)).isEqualTo(3);
        assertThat(jdbcTemplate.preparedStatement.value(13)).isEqualTo(Timestamp.valueOf(nextRetryTime));
    }

    @Test
    void findDueMessagesUsesPendingStatusTimeLimitAndMapsAllColumns() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcLocalMessageRepository repository = new JdbcLocalMessageRepository(jdbcTemplate, "framework_local_message");
        LocalDateTime now = LocalDateTime.of(2026, 6, 25, 11, 0);

        List<LocalMessage> messages = repository.findDueMessages(now, 10);

        assertThat(jdbcTemplate.querySql)
                .contains("FROM framework_local_message")
                .contains("status = ?")
                .contains("next_retry_time <= ?")
                .contains("LIMIT ?");
        assertThat(jdbcTemplate.queryArgs).containsExactly(
                LocalMessageStatus.PENDING.name(),
                Timestamp.valueOf(now),
                10
        );
        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message.getId()).isEqualTo(7L);
            assertThat(message.getMessageId()).isEqualTo("local-msg-1");
            assertThat(message.getTraceId()).isEqualTo("trace-1");
            assertThat(message.getParentMessageId()).isEqualTo("parent-msg-1");
            assertThat(message.getTopic()).isEqualTo("order.created");
            assertThat(message.getBusinessKey()).isEqualTo("ORD-1");
            assertThat(message.getTenantId()).isEqualTo("tenant-a");
            assertThat(message.getOperator()).isEqualTo("ops-user");
            assertThat(message.getSource()).isEqualTo("order-service");
            assertThat(message.getPayload()).isEqualTo("{\"id\":1}");
            assertThat(message.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
            assertThat(message.getRetryCount()).isEqualTo(2);
            assertThat(message.getMaxRetry()).isEqualTo(3);
            assertThat(message.getNextRetryTime()).isEqualTo(LocalDateTime.of(2026, 6, 25, 10, 30));
            assertThat(message.getErrorMessage()).isEqualTo("temporary");
            assertThat(message.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 6, 25, 9, 0));
            assertThat(message.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 6, 25, 9, 30));
        });
    }

    @Test
    void updateReportsAffectedRowsAndBindsMessageId() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcLocalMessageRepository repository = new JdbcLocalMessageRepository(jdbcTemplate, "framework_local_message");
        LocalMessage message = new LocalMessage()
                .setId(9L)
                .setMessageId("local-msg-9")
                .setTraceId("trace-9")
                .setTopic("order.created")
                .setBusinessKey("ORD-9")
                .setPayload("{\"id\":9}")
                .setStatus(LocalMessageStatus.SUCCESS)
                .setRetryCount(0)
                .setMaxRetry(3)
                .setCreateTime(LocalDateTime.of(2026, 6, 25, 9, 0));

        assertThat(repository.update(message)).isTrue();
        assertThat(jdbcTemplate.updateSql)
                .contains("UPDATE framework_local_message SET")
                .contains("WHERE id = ?");
        assertThat(jdbcTemplate.updateArgs.get(0)).isEqualTo("local-msg-9");
        assertThat(jdbcTemplate.updateArgs.get(9)).isEqualTo(LocalMessageStatus.SUCCESS.name());
        assertThat(jdbcTemplate.updateArgs.get(16)).isEqualTo(9L);

        jdbcTemplate.updateResult = 0;

        assertThat(repository.update(message)).isFalse();
    }

    @Test
    void deleteReportsAffectedRows() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcLocalMessageRepository repository = new JdbcLocalMessageRepository(jdbcTemplate, "framework_local_message");

        assertThat(repository.delete(9L)).isTrue();
        assertThat(jdbcTemplate.updateSql).isEqualTo("DELETE FROM framework_local_message WHERE id = ?");
        assertThat(jdbcTemplate.updateArgs).containsExactly(9L);

        jdbcTemplate.updateResult = 0;

        assertThat(repository.delete(9L)).isFalse();
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {

        private final CapturingPreparedStatement preparedStatement = new CapturingPreparedStatement();
        private String preparedSql;
        private String querySql;
        private List<Object> queryArgs = List.of();
        private String updateSql;
        private List<Object> updateArgs = List.of();
        private int updateResult = 1;

        @Override
        public int update(PreparedStatementCreator psc, KeyHolder generatedKeyHolder) {
            try {
                Connection connection = connectionProxy();
                psc.createPreparedStatement(connection);
                generatedKeyHolder.getKeyList().add(Map.of("id", 42L));
                return 1;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            querySql = sql;
            queryArgs = List.of(args);
            try {
                return new ArrayList<>(List.of(rowMapper.mapRow(resultSet(), 0)));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public int update(String sql, Object... args) {
            updateSql = sql;
            updateArgs = args == null ? List.of() : new ArrayList<>(java.util.Arrays.asList(args));
            return updateResult;
        }

        private ResultSet resultSet() throws Exception {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 7L);
            row.put("message_id", "local-msg-1");
            row.put("trace_id", "trace-1");
            row.put("parent_message_id", "parent-msg-1");
            row.put("topic", "order.created");
            row.put("business_key", "ORD-1");
            row.put("tenant_id", "tenant-a");
            row.put("operator", "ops-user");
            row.put("source", "order-service");
            row.put("payload", "{\"id\":1}");
            row.put("status", LocalMessageStatus.PENDING.name());
            row.put("retry_count", 2);
            row.put("max_retry", 3);
            row.put("next_retry_time", Timestamp.valueOf(LocalDateTime.of(2026, 6, 25, 10, 30)));
            row.put("error_message", "temporary");
            row.put("create_time", Timestamp.valueOf(LocalDateTime.of(2026, 6, 25, 9, 0)));
            row.put("update_time", Timestamp.valueOf(LocalDateTime.of(2026, 6, 25, 9, 30)));
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getLong".equals(name) || "getInt".equals(name)
                                || "getString".equals(name) || "getTimestamp".equals(name)) {
                            return row.get(args[0]);
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private Connection connectionProxy() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            preparedSql = (String) args[0];
                            assertThat(args[1]).isEqualTo(Statement.RETURN_GENERATED_KEYS);
                            return preparedStatement.proxy();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static class CapturingPreparedStatement {

        private final Map<Integer, Object> values = new LinkedHashMap<>();

        private PreparedStatement proxy() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        if (method.getName().startsWith("set") && args != null && args.length >= 2
                                && args[0] instanceof Integer index) {
                            values.put(index, args[1]);
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private Object value(int index) {
            return values.get(index);
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
