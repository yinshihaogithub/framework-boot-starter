package com.framework.admin.excel;

import com.framework.admin.support.AdminTextSupport;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ExcelAdminMapperSupport {

    private ExcelAdminMapperSupport() {
    }

    static List<ExcelAdminModels.Task> listTasks(ExcelAdminMapper mapper,
                                                 String taskType,
                                                 String status,
                                                 int pageNum,
                                                 int pageSize) {
        return mapper.listTasks(text(taskType), text(status), offset(pageNum, pageSize), pageSize);
    }

    static long countTasks(ExcelAdminMapper mapper, String taskType, String status) {
        return mapper.countTasks(text(taskType), text(status));
    }

    static Map<String, Long> stats(ExcelAdminMapper mapper) {
        return Map.of(
                "total", mapper.countAllTasks(),
                "success", mapper.countTasksByStatus("SUCCESS"),
                "failed", mapper.countTasksByStatus("FAILED"),
                "import", mapper.countTasksByType("IMPORT"),
                "export", mapper.countTasksByType("EXPORT"));
    }

    static Long createTask(ExcelAdminMapper mapper, ExcelAdminModels.Task task) {
        int inserted = mapper.insertTask(task);
        if (inserted <= 0 || task.getId() == null) {
            throw new IllegalStateException("excel task insert failed");
        }
        return task.getId();
    }

    static void createError(ExcelAdminMapper mapper, Long taskId, int rowIndex, String errorMessage, String rawData) {
        int inserted = mapper.insertError(taskId, rowIndex, errorMessage, rawData);
        if (inserted <= 0) {
            throw new IllegalStateException("excel error insert failed");
        }
    }

    static Long createTaskWithErrors(ExcelAdminMapper mapper,
                                     ExcelAdminModels.Task task,
                                     List<ExcelAdminModels.ErrorRecord> errors) {
        Long taskId = createTask(mapper, task);
        if (errors != null) {
            for (ExcelAdminModels.ErrorRecord error : errors) {
                createError(mapper, taskId, rowIndex(error), error.getErrorMessage(), error.getRawData());
            }
        }
        return taskId;
    }

    static List<ExcelAdminModels.ErrorRecord> listErrors(ExcelAdminMapper mapper, Long taskId) {
        return mapper.listErrors(taskId);
    }

    private static int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * pageSize;
    }

    private static String text(String value) {
        String text = AdminTextSupport.trimToNull(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private static int rowIndex(ExcelAdminModels.ErrorRecord error) {
        return error.getRowIndex() == null ? 0 : error.getRowIndex();
    }
}
