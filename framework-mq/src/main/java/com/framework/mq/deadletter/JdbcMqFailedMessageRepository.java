package com.framework.mq.deadletter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MySQL-backed failed message repository.
 */
public class JdbcMqFailedMessageRepository implements MqFailedMessageRepository {

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final RowMapper<MqFailedMessage> rowMapper = (rs, rowNum) -> {
        MqFailedMessage message = new MqFailedMessage();
        message.setId(rs.getLong("id"));
        message.setMessageId(rs.getString("message_id"));
        message.setTraceId(rs.getString("trace_id"));
        message.setParentMessageId(rs.getString("parent_message_id"));
        message.setBusinessKey(rs.getString("business_key"));
        message.setMessageType(rs.getString("message_type"));
        message.setExchange(rs.getString("exchange_name"));
        message.setRoutingKey(rs.getString("routing_key"));
        message.setQueueName(rs.getString("queue_name"));
        message.setPayload(rs.getString("payload"));
        message.setErrorMessage(rs.getString("error_message"));
        message.setErrorStack(rs.getString("error_stack"));
        message.setRetryCount(rs.getInt("retry_count"));
        message.setMaxRetry(rs.getInt("max_retry"));
        message.setStatus(rs.getString("status"));
        message.setNextRetryTime(rs.getTimestamp("next_retry_time"));
        message.setSource(rs.getString("source"));
        message.setTenantId(rs.getString("tenant_id"));
        message.setOperator(rs.getString("operator"));
        message.setCompensateRemark(rs.getString("compensate_remark"));
        message.setCreateTime(rs.getTimestamp("create_time"));
        message.setUpdateTime(rs.getTimestamp("update_time"));
        return message;
    };

    public JdbcMqFailedMessageRepository(JdbcTemplate jdbcTemplate, String tableName) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.tableName = validateTableName(tableName);
    }

    @Override
    public MqFailedMessage save(MqFailedMessage message) {
        if (message.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO %s
                        (message_id, trace_id, parent_message_id, business_key, message_type,
                         exchange_name, routing_key, queue_name, payload, error_message, error_stack,
                         retry_count, max_retry, status, next_retry_time, source, tenant_id, operator,
                         compensate_remark, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(tableName), Statement.RETURN_GENERATED_KEYS);
                bind(ps, message);
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key != null) {
                message.setId(key.longValue());
            }
            return message;
        }

        jdbcTemplate.update("""
                        UPDATE %s SET
                            message_id = ?,
                            trace_id = ?,
                            parent_message_id = ?,
                            business_key = ?,
                            message_type = ?,
                            exchange_name = ?,
                            routing_key = ?,
                            queue_name = ?,
                            payload = ?,
                            error_message = ?,
                            error_stack = ?,
                            retry_count = ?,
                            max_retry = ?,
                            status = ?,
                            next_retry_time = ?,
                            source = ?,
                            tenant_id = ?,
                            operator = ?,
                            compensate_remark = ?,
                            create_time = ?,
                            update_time = ?
                        WHERE id = ?
                        """.formatted(tableName),
                message.getMessageId(),
                message.getTraceId(),
                message.getParentMessageId(),
                message.getBusinessKey(),
                message.getMessageType(),
                message.getExchange(),
                message.getRoutingKey(),
                message.getQueueName(),
                message.getPayload(),
                message.getErrorMessage(),
                message.getErrorStack(),
                message.getRetryCount(),
                message.getMaxRetry(),
                message.getStatus(),
                toTimestamp(message.getNextRetryTime()),
                message.getSource(),
                message.getTenantId(),
                message.getOperator(),
                message.getCompensateRemark(),
                toTimestamp(message.getCreateTime()),
                toTimestamp(message.getUpdateTime()),
                message.getId());
        return message;
    }

    @Override
    public Optional<MqFailedMessage> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM %s WHERE id = ?".formatted(tableName), rowMapper, id)
                .stream()
                .findFirst();
    }

    @Override
    public List<MqFailedMessage> findAll() {
        return jdbcTemplate.query("SELECT * FROM %s ORDER BY id ASC".formatted(tableName), rowMapper);
    }

    @Override
    public boolean deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM %s WHERE id = ?".formatted(tableName), id) > 0;
    }

    @Override
    public int deleteProcessed() {
        return jdbcTemplate.update("""
                DELETE FROM %s
                WHERE status IN (?, ?, ?)
                """.formatted(tableName),
                MqFailedMessage.STATUS_SUCCESS,
                MqFailedMessage.STATUS_EXHAUSTED,
                MqFailedMessage.STATUS_MANUAL);
    }

    public static String validateTableName(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("mq failed message tableName must match [A-Za-z0-9_]+");
        }
        return tableName;
    }

    private static void bind(PreparedStatement ps, MqFailedMessage message) throws java.sql.SQLException {
        ps.setString(1, message.getMessageId());
        ps.setString(2, message.getTraceId());
        ps.setString(3, message.getParentMessageId());
        ps.setString(4, message.getBusinessKey());
        ps.setString(5, message.getMessageType());
        ps.setString(6, message.getExchange());
        ps.setString(7, message.getRoutingKey());
        ps.setString(8, message.getQueueName());
        ps.setString(9, message.getPayload());
        ps.setString(10, message.getErrorMessage());
        ps.setString(11, message.getErrorStack());
        ps.setInt(12, defaultInt(message.getRetryCount()));
        ps.setInt(13, defaultInt(message.getMaxRetry()));
        ps.setString(14, message.getStatus());
        ps.setTimestamp(15, toTimestamp(message.getNextRetryTime()));
        ps.setString(16, message.getSource());
        ps.setString(17, message.getTenantId());
        ps.setString(18, message.getOperator());
        ps.setString(19, message.getCompensateRemark());
        ps.setTimestamp(20, toTimestamp(message.getCreateTime()));
        ps.setTimestamp(21, toTimestamp(message.getUpdateTime()));
    }

    private static int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static Timestamp toTimestamp(java.util.Date value) {
        return value == null ? null : new Timestamp(value.getTime());
    }
}
