package com.framework.admin.notify;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class NotifyAdminRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<NotifyAdminModels.Template> templateMapper = (rs, rowNum) -> new NotifyAdminModels.Template()
            .setId(rs.getLong("id"))
            .setTemplateCode(rs.getString("template_code"))
            .setTemplateName(rs.getString("template_name"))
            .setChannel(rs.getString("channel"))
            .setTitle(rs.getString("title"))
            .setContent(rs.getString("content"))
            .setReceivers(split(rs.getString("receivers")))
            .setWebhookUrl(rs.getString("webhook_url"))
            .setStatus(rs.getString("status"))
            .setCreateTime(format(rs.getTimestamp("create_time")))
            .setUpdateTime(format(rs.getTimestamp("update_time")));

    private final RowMapper<NotifyAdminModels.Record> recordMapper = (rs, rowNum) -> new NotifyAdminModels.Record()
            .setId(rs.getLong("id"))
            .setTemplateCode(rs.getString("template_code"))
            .setChannel(rs.getString("channel"))
            .setTitle(rs.getString("title"))
            .setContent(rs.getString("content"))
            .setReceivers(split(rs.getString("receivers")))
            .setWebhookUrl(rs.getString("webhook_url"))
            .setSuccess(rs.getBoolean("success"))
            .setResultMessage(rs.getString("result_message"))
            .setTraceId(rs.getString("trace_id"))
            .setOperatorName(rs.getString("operator_name"))
            .setCreateTime(format(rs.getTimestamp("create_time")));

    public NotifyAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NotifyAdminModels.Template> listTemplates(String keyword, String channel, String status,
                                                          int pageNum, int pageSize) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT * FROM framework_notify_template
                WHERE (? IS NULL OR template_code LIKE ? OR template_name LIKE ? OR title LIKE ?)
                  AND (? IS NULL OR channel = ?)
                  AND (? IS NULL OR status = ?)
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, templateMapper,
                like, like, like, like,
                blankToNull(channel), blankToNull(channel),
                blankToNull(status), blankToNull(status),
                pageSize, offset(pageNum, pageSize));
    }

    public long countTemplates(String keyword, String channel, String status) {
        String like = like(keyword);
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM framework_notify_template
                WHERE (? IS NULL OR template_code LIKE ? OR template_name LIKE ? OR title LIKE ?)
                  AND (? IS NULL OR channel = ?)
                  AND (? IS NULL OR status = ?)
                """, Long.class,
                like, like, like, like,
                blankToNull(channel), blankToNull(channel),
                blankToNull(status), blankToNull(status));
        return count == null ? 0 : count;
    }

    public Optional<NotifyAdminModels.Template> findTemplate(Long id) {
        List<NotifyAdminModels.Template> templates = jdbcTemplate.query(
                "SELECT * FROM framework_notify_template WHERE id = ?", templateMapper, id);
        return templates.stream().findFirst();
    }

    public Long createTemplate(NotifyAdminModels.TemplateRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO framework_notify_template
                    (template_code, template_name, channel, title, content, receivers, webhook_url, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bindTemplate(ps, request);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void updateTemplate(Long id, NotifyAdminModels.TemplateRequest request) {
        jdbcTemplate.update("""
                UPDATE framework_notify_template SET
                    template_code = ?, template_name = ?, channel = ?, title = ?, content = ?,
                    receivers = ?, webhook_url = ?, status = ?
                WHERE id = ?
                """,
                text(request.getTemplateCode()), text(request.getTemplateName()), text(request.getChannel()),
                text(request.getTitle()), text(request.getContent()), join(request.getReceivers()),
                text(request.getWebhookUrl()), defaultStatus(request.getStatus()), id);
    }

    public void deleteTemplate(Long id) {
        jdbcTemplate.update("DELETE FROM framework_notify_template WHERE id = ?", id);
    }

    public Long createRecord(NotifyAdminModels.Record record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO framework_notify_record
                    (template_code, channel, title, content, receivers, webhook_url,
                     success, result_message, trace_id, operator_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, record.getTemplateCode());
            ps.setString(2, record.getChannel());
            ps.setString(3, record.getTitle());
            ps.setString(4, record.getContent());
            ps.setString(5, join(record.getReceivers()));
            ps.setString(6, record.getWebhookUrl());
            ps.setBoolean(7, Boolean.TRUE.equals(record.getSuccess()));
            ps.setString(8, record.getResultMessage());
            ps.setString(9, record.getTraceId());
            ps.setString(10, record.getOperatorName());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public List<NotifyAdminModels.Record> listRecords(String channel, Boolean success, int pageNum, int pageSize) {
        return jdbcTemplate.query("""
                SELECT * FROM framework_notify_record
                WHERE (? IS NULL OR channel = ?)
                  AND (? IS NULL OR success = ?)
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, recordMapper,
                blankToNull(channel), blankToNull(channel),
                success, success,
                pageSize, offset(pageNum, pageSize));
    }

    public long countRecords(String channel, Boolean success) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM framework_notify_record
                WHERE (? IS NULL OR channel = ?)
                  AND (? IS NULL OR success = ?)
                """, Long.class,
                blankToNull(channel), blankToNull(channel),
                success, success);
        return count == null ? 0 : count;
    }

    public long countRecordsBySuccess(boolean success) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM framework_notify_record WHERE success = ?", Long.class, success);
        return count == null ? 0 : count;
    }

    public long countTemplatesByStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM framework_notify_template WHERE status = ?", Long.class, status);
        return count == null ? 0 : count;
    }

    private void bindTemplate(PreparedStatement ps, NotifyAdminModels.TemplateRequest request) throws java.sql.SQLException {
        ps.setString(1, text(request.getTemplateCode()));
        ps.setString(2, text(request.getTemplateName()));
        ps.setString(3, text(request.getChannel()));
        ps.setString(4, text(request.getTitle()));
        ps.setString(5, text(request.getContent()));
        ps.setString(6, join(request.getReceivers()));
        ps.setString(7, text(request.getWebhookUrl()));
        ps.setString(8, defaultStatus(request.getStatus()));
    }

    private static int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * pageSize;
    }

    private static String like(String value) {
        String text = text(value);
        return text == null ? null : "%" + text + "%";
    }

    private static String blankToNull(String value) {
        return text(value);
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String defaultStatus(String status) {
        String text = text(status);
        return text == null ? "ENABLED" : text;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList());
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static String format(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime time = timestamp.toLocalDateTime();
        return time.toString().replace('T', ' ');
    }
}
