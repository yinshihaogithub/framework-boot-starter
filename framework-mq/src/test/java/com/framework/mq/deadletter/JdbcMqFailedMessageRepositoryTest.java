package com.framework.mq.deadletter;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcMqFailedMessageRepositoryTest {

    @Test
    void constructorRejectsNullJdbcTemplate() {
        assertThatThrownBy(() -> new JdbcMqFailedMessageRepository(null, "framework_mq_failed_message"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jdbcTemplate");
    }

    @Test
    void insertBindsAllCompensationColumnsAndGeneratedKey() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcMqFailedMessageRepository repository =
                new JdbcMqFailedMessageRepository(jdbcTemplate, "framework_mq_failed_message");
        Date nextRetryTime = new Date(1_800_000L);
        Date createTime = new Date(1_000_000L);
        Date updateTime = new Date(1_200_000L);
        MqFailedMessage message = message(nextRetryTime, createTime, updateTime);

        MqFailedMessage saved = repository.save(message);

        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(jdbcTemplate.preparedSql).contains("INSERT INTO framework_mq_failed_message");
        assertThat(jdbcTemplate.preparedStatement.value(1)).isEqualTo("msg-1");
        assertThat(jdbcTemplate.preparedStatement.value(2)).isEqualTo("trace-1");
        assertThat(jdbcTemplate.preparedStatement.value(3)).isEqualTo("parent-msg-1");
        assertThat(jdbcTemplate.preparedStatement.value(4)).isEqualTo("ORD-1");
        assertThat(jdbcTemplate.preparedStatement.value(5)).isEqualTo("OrderCreated");
        assertThat(jdbcTemplate.preparedStatement.value(6)).isEqualTo("order.exchange");
        assertThat(jdbcTemplate.preparedStatement.value(7)).isEqualTo("order.created");
        assertThat(jdbcTemplate.preparedStatement.value(8)).isEqualTo("order.queue");
        assertThat(jdbcTemplate.preparedStatement.value(12)).isEqualTo(2);
        assertThat(jdbcTemplate.preparedStatement.value(13)).isEqualTo(5);
        assertThat(jdbcTemplate.preparedStatement.value(14)).isEqualTo(MqFailedMessage.STATUS_PENDING);
        assertThat(jdbcTemplate.preparedStatement.value(15)).isEqualTo(new Timestamp(nextRetryTime.getTime()));
        assertThat(jdbcTemplate.preparedStatement.value(16)).isEqualTo(MqFailedMessage.SOURCE_DEAD_LETTER);
        assertThat(jdbcTemplate.preparedStatement.value(17)).isEqualTo("tenant-a");
        assertThat(jdbcTemplate.preparedStatement.value(18)).isEqualTo("ops-user");
        assertThat(jdbcTemplate.preparedStatement.value(19)).isEqualTo("manual retry");
    }

    @Test
    void findByIdMapsAllCompensationColumns() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcMqFailedMessageRepository repository =
                new JdbcMqFailedMessageRepository(jdbcTemplate, "framework_mq_failed_message");

        MqFailedMessage message = repository.findById(7L).orElseThrow();

        assertThat(jdbcTemplate.querySql).contains("FROM framework_mq_failed_message").contains("WHERE id = ?");
        assertThat(jdbcTemplate.queryArgs).containsExactly(7L);
        assertThat(message.getId()).isEqualTo(7L);
        assertThat(message.getMessageId()).isEqualTo("msg-1");
        assertThat(message.getTraceId()).isEqualTo("trace-1");
        assertThat(message.getParentMessageId()).isEqualTo("parent-msg-1");
        assertThat(message.getBusinessKey()).isEqualTo("ORD-1");
        assertThat(message.getMessageType()).isEqualTo("OrderCreated");
        assertThat(message.getExchange()).isEqualTo("order.exchange");
        assertThat(message.getRoutingKey()).isEqualTo("order.created");
        assertThat(message.getQueueName()).isEqualTo("order.queue");
        assertThat(message.getPayload()).isEqualTo("{\"id\":1}");
        assertThat(message.getErrorMessage()).isEqualTo("downstream unavailable");
        assertThat(message.getErrorStack()).isEqualTo("stacktrace");
        assertThat(message.getRetryCount()).isEqualTo(2);
        assertThat(message.getMaxRetry()).isEqualTo(5);
        assertThat(message.getStatus()).isEqualTo(MqFailedMessage.STATUS_PENDING);
        assertThat(message.getNextRetryTime()).isEqualTo(new Timestamp(1_800_000L));
        assertThat(message.getSource()).isEqualTo(MqFailedMessage.SOURCE_DEAD_LETTER);
        assertThat(message.getTenantId()).isEqualTo("tenant-a");
        assertThat(message.getOperator()).isEqualTo("ops-user");
        assertThat(message.getCompensateRemark()).isEqualTo("manual retry");
        assertThat(message.getCreateTime()).isEqualTo(new Timestamp(1_000_000L));
        assertThat(message.getUpdateTime()).isEqualTo(new Timestamp(1_200_000L));
    }

    @Test
    void updateReportsAffectedRowsAndBindsAllColumns() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcMqFailedMessageRepository repository =
                new JdbcMqFailedMessageRepository(jdbcTemplate, "framework_mq_failed_message");
        MqFailedMessage message = message(new Date(1_800_000L), new Date(1_000_000L), new Date(1_200_000L));
        message.setId(9L);
        message.setStatus(MqFailedMessage.STATUS_MANUAL);

        assertThat(repository.update(message)).isTrue();
        assertThat(jdbcTemplate.updateSql)
                .contains("UPDATE framework_mq_failed_message SET")
                .contains("WHERE id = ?");
        assertThat(jdbcTemplate.updateArgs.get(0)).isEqualTo("msg-1");
        assertThat(jdbcTemplate.updateArgs.get(13)).isEqualTo(MqFailedMessage.STATUS_MANUAL);
        assertThat(jdbcTemplate.updateArgs.get(21)).isEqualTo(9L);

        jdbcTemplate.updateResult = 0;

        assertThat(repository.update(message)).isFalse();
    }

    @Test
    void deleteProcessedOnlyRemovesTerminalStatuses() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcMqFailedMessageRepository repository =
                new JdbcMqFailedMessageRepository(jdbcTemplate, "framework_mq_failed_message");

        int deleted = repository.deleteProcessed();

        assertThat(deleted).isEqualTo(3);
        assertThat(jdbcTemplate.updateSql)
                .contains("DELETE FROM framework_mq_failed_message")
                .contains("status IN");
        assertThat(jdbcTemplate.updateArgs).containsExactly(
                MqFailedMessage.STATUS_SUCCESS,
                MqFailedMessage.STATUS_EXHAUSTED,
                MqFailedMessage.STATUS_MANUAL
        );
    }

    private static MqFailedMessage message(Date nextRetryTime, Date createTime, Date updateTime) {
        MqFailedMessage message = new MqFailedMessage();
        message.setMessageId("msg-1");
        message.setTraceId("trace-1");
        message.setParentMessageId("parent-msg-1");
        message.setBusinessKey("ORD-1");
        message.setMessageType("OrderCreated");
        message.setExchange("order.exchange");
        message.setRoutingKey("order.created");
        message.setQueueName("order.queue");
        message.setPayload("{\"id\":1}");
        message.setErrorMessage("downstream unavailable");
        message.setErrorStack("stacktrace");
        message.setRetryCount(2);
        message.setMaxRetry(5);
        message.setStatus(MqFailedMessage.STATUS_PENDING);
        message.setNextRetryTime(nextRetryTime);
        message.setSource(MqFailedMessage.SOURCE_DEAD_LETTER);
        message.setTenantId("tenant-a");
        message.setOperator("ops-user");
        message.setCompensateRemark("manual retry");
        message.setCreateTime(createTime);
        message.setUpdateTime(updateTime);
        return message;
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {

        private final CapturingPreparedStatement preparedStatement = new CapturingPreparedStatement();
        private String preparedSql;
        private String querySql;
        private List<Object> queryArgs = List.of();
        private String updateSql;
        private List<Object> updateArgs = List.of();
        private int updateResult = 3;

        @Override
        public int update(PreparedStatementCreator psc, KeyHolder generatedKeyHolder) {
            try {
                psc.createPreparedStatement(connectionProxy());
                generatedKeyHolder.getKeyList().add(Map.of("id", 42L));
                return 1;
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

        private ResultSet resultSet() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 7L);
            row.put("message_id", "msg-1");
            row.put("trace_id", "trace-1");
            row.put("parent_message_id", "parent-msg-1");
            row.put("business_key", "ORD-1");
            row.put("message_type", "OrderCreated");
            row.put("exchange_name", "order.exchange");
            row.put("routing_key", "order.created");
            row.put("queue_name", "order.queue");
            row.put("payload", "{\"id\":1}");
            row.put("error_message", "downstream unavailable");
            row.put("error_stack", "stacktrace");
            row.put("retry_count", 2);
            row.put("max_retry", 5);
            row.put("status", MqFailedMessage.STATUS_PENDING);
            row.put("next_retry_time", new Timestamp(1_800_000L));
            row.put("source", MqFailedMessage.SOURCE_DEAD_LETTER);
            row.put("tenant_id", "tenant-a");
            row.put("operator", "ops-user");
            row.put("compensate_remark", "manual retry");
            row.put("create_time", new Timestamp(1_000_000L));
            row.put("update_time", new Timestamp(1_200_000L));
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
