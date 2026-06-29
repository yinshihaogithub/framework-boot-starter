package com.framework.admin.excel;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class ExcelAdminRepository {

    private final ExcelAdminMapper mapper;

    public ExcelAdminRepository(ExcelAdminMapper mapper) {
        this.mapper = mapper;
    }

    public List<ExcelAdminModels.Task> listTasks(String taskType, String status, int pageNum, int pageSize) {
        return mapper.listTasks(text(taskType), text(status), offset(pageNum, pageSize), pageSize);
    }

    public long countTasks(String taskType, String status) {
        return mapper.countTasks(text(taskType), text(status));
    }

    public Map<String, Long> stats() {
        return Map.of(
                "total", mapper.countAllTasks(),
                "success", mapper.countTasksByStatus("SUCCESS"),
                "failed", mapper.countTasksByStatus("FAILED"),
                "import", mapper.countTasksByType("IMPORT"),
                "export", mapper.countTasksByType("EXPORT"));
    }

    public Long createTask(ExcelAdminModels.Task task) {
        mapper.insertTask(task);
        return task.getId();
    }

    public void createError(Long taskId, int rowIndex, String errorMessage, String rawData) {
        mapper.insertError(taskId, rowIndex, errorMessage, rawData);
    }

    public List<ExcelAdminModels.ErrorRecord> listErrors(Long taskId) {
        return mapper.listErrors(taskId);
    }

    private static int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * pageSize;
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

}
