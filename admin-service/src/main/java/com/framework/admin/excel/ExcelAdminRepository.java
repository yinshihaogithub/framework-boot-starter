package com.framework.admin.excel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class ExcelAdminRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ExcelAdminModels.Task> taskMapper = (rs, rowNum) -> new ExcelAdminModels.Task()
            .setId(rs.getLong("id"))
            .setTaskName(rs.getString("task_name"))
            .setTaskType(rs.getString("task_type"))
            .setBizType(rs.getString("biz_type"))
            .setStatus(rs.getString("status"))
            .setFilename(rs.getString("filename"))
            .setTotalRows(rs.getInt("total_rows"))
            .setSuccessRows(rs.getInt("success_rows"))
            .setFailureRows(rs.getInt("failure_rows"))
            .setOperatorName(rs.getString("operator_name"))
            .setErrorMessage(rs.getString("error_message"))
            .setCreateTime(format(rs.getTimestamp("create_time")))
            .setUpdateTime(format(rs.getTimestamp("update_time")));

    private final RowMapper<ExcelAdminModels.ErrorRecord> errorMapper = (rs, rowNum) -> new ExcelAdminModels.ErrorRecord()
            .setId(rs.getLong("id"))
            .setTaskId(rs.getLong("task_id"))
            .setRowIndex(rs.getInt("row_index"))
            .setErrorMessage(rs.getString("error_message"))
            .setRawData(rs.getString("raw_data"))
            .setCreateTime(format(rs.getTimestamp("create_time")));

    public ExcelAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExcelAdminModels.Task> listTasks(String taskType, String status, int pageNum, int pageSize) {
        return jdbcTemplate.query("""
                SELECT * FROM framework_excel_task
                WHERE (? IS NULL OR task_type = ?)
                  AND (? IS NULL OR status = ?)
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, taskMapper,
                text(taskType), text(taskType),
                text(status), text(status),
                pageSize, offset(pageNum, pageSize));
    }

    public long countTasks(String taskType, String status) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM framework_excel_task
                WHERE (? IS NULL OR task_type = ?)
                  AND (? IS NULL OR status = ?)
                """, Long.class,
                text(taskType), text(taskType),
                text(status), text(status));
        return count == null ? 0 : count;
    }

    public Map<String, Long> stats() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM framework_excel_task", Long.class);
        Long success = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM framework_excel_task WHERE status = 'SUCCESS'", Long.class);
        Long failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM framework_excel_task WHERE status = 'FAILED'", Long.class);
        Long importing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM framework_excel_task WHERE task_type = 'IMPORT'", Long.class);
        Long exporting = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM framework_excel_task WHERE task_type = 'EXPORT'", Long.class);
        return Map.of(
                "total", value(total),
                "success", value(success),
                "failed", value(failed),
                "import", value(importing),
                "export", value(exporting));
    }

    public Long createTask(ExcelAdminModels.Task task) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO framework_excel_task
                    (task_name, task_type, biz_type, status, filename, total_rows, success_rows,
                     failure_rows, operator_name, error_message)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, task.getTaskName());
            ps.setString(2, task.getTaskType());
            ps.setString(3, task.getBizType());
            ps.setString(4, task.getStatus());
            ps.setString(5, task.getFilename());
            ps.setInt(6, task.getTotalRows() == null ? 0 : task.getTotalRows());
            ps.setInt(7, task.getSuccessRows() == null ? 0 : task.getSuccessRows());
            ps.setInt(8, task.getFailureRows() == null ? 0 : task.getFailureRows());
            ps.setString(9, task.getOperatorName());
            ps.setString(10, task.getErrorMessage());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void createError(Long taskId, int rowIndex, String errorMessage, String rawData) {
        jdbcTemplate.update("""
                INSERT INTO framework_excel_error (task_id, row_index, error_message, raw_data)
                VALUES (?, ?, ?, ?)
                """, taskId, rowIndex, errorMessage, rawData);
    }

    public List<ExcelAdminModels.ErrorRecord> listErrors(Long taskId) {
        return jdbcTemplate.query("""
                SELECT * FROM framework_excel_error
                WHERE task_id = ?
                ORDER BY row_index ASC, id ASC
                """, errorMapper, taskId);
    }

    private static int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * pageSize;
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static String format(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime time = timestamp.toLocalDateTime();
        return time.toString().replace('T', ' ');
    }
}
