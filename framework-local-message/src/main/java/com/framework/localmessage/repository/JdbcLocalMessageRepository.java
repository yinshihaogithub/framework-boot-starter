package com.framework.localmessage.repository;

import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-backed local message table repository.
 */
public class JdbcLocalMessageRepository implements LocalMessageRepository {

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final RowMapper<LocalMessage> rowMapper = (rs, rowNum) -> new LocalMessage()
            .setId(rs.getLong("id"))
            .setTopic(rs.getString("topic"))
            .setBusinessKey(rs.getString("business_key"))
            .setPayload(rs.getString("payload"))
            .setStatus(LocalMessageStatus.valueOf(rs.getString("status")))
            .setRetryCount(rs.getInt("retry_count"))
            .setMaxRetry(rs.getInt("max_retry"))
            .setNextRetryTime(toLocalDateTime(rs.getTimestamp("next_retry_time")))
            .setErrorMessage(rs.getString("error_message"))
            .setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")))
            .setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));

    public JdbcLocalMessageRepository(JdbcTemplate jdbcTemplate, String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = validateTableName(tableName);
    }

    @Override
    public LocalMessage save(LocalMessage message) {
        LocalDateTime now = LocalDateTime.now();
        if (message.getCreateTime() == null) {
            message.setCreateTime(now);
        }
        message.setUpdateTime(now);

        if (message.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO %s
                        (topic, business_key, payload, status, retry_count, max_retry,
                         next_retry_time, error_message, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(tableName), Statement.RETURN_GENERATED_KEYS);
                bindMessage(ps, message);
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
                            topic = ?,
                            business_key = ?,
                            payload = ?,
                            status = ?,
                            retry_count = ?,
                            max_retry = ?,
                            next_retry_time = ?,
                            error_message = ?,
                            create_time = ?,
                            update_time = ?
                        WHERE id = ?
                        """.formatted(tableName),
                message.getTopic(),
                message.getBusinessKey(),
                message.getPayload(),
                message.getStatus().name(),
                message.getRetryCount(),
                message.getMaxRetry(),
                toTimestamp(message.getNextRetryTime()),
                message.getErrorMessage(),
                toTimestamp(message.getCreateTime()),
                toTimestamp(message.getUpdateTime()),
                message.getId());
        return message;
    }

    @Override
    public Optional<LocalMessage> findById(Long id) {
        List<LocalMessage> messages = jdbcTemplate.query(
                "SELECT * FROM %s WHERE id = ?".formatted(tableName),
                rowMapper,
                id);
        return messages.stream().findFirst();
    }

    @Override
    public List<LocalMessage> findDueMessages(LocalDateTime now, int limit) {
        return jdbcTemplate.query("""
                        SELECT * FROM %s
                        WHERE status = ?
                          AND (next_retry_time IS NULL OR next_retry_time <= ?)
                        ORDER BY create_time ASC
                        LIMIT ?
                        """.formatted(tableName),
                rowMapper,
                LocalMessageStatus.PENDING.name(),
                toTimestamp(now),
                limit);
    }

    @Override
    public List<LocalMessage> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM %s ORDER BY id ASC".formatted(tableName),
                rowMapper);
    }

    @Override
    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM %s WHERE id = ?".formatted(tableName), id);
    }

    public static String validateTableName(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("local message tableName must match [A-Za-z0-9_]+");
        }
        return tableName;
    }

    private static void bindMessage(PreparedStatement ps, LocalMessage message) throws java.sql.SQLException {
        ps.setString(1, message.getTopic());
        ps.setString(2, message.getBusinessKey());
        ps.setString(3, message.getPayload());
        ps.setString(4, message.getStatus().name());
        ps.setInt(5, message.getRetryCount());
        ps.setInt(6, message.getMaxRetry());
        ps.setTimestamp(7, toTimestamp(message.getNextRetryTime()));
        ps.setString(8, message.getErrorMessage());
        ps.setTimestamp(9, toTimestamp(message.getCreateTime()));
        ps.setTimestamp(10, toTimestamp(message.getUpdateTime()));
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
