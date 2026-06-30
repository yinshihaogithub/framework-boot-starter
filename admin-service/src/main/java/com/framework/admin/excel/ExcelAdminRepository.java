package com.framework.admin.excel;

import com.framework.admin.support.AdminTextSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Repository
public class ExcelAdminRepository {

    private final ExcelAdminMapper mapper;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public ExcelAdminRepository(ExcelAdminMapper mapper, TransactionTemplate transactionTemplate) {
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
    }

    public ExcelAdminRepository(ExcelAdminMapper mapper) {
        this(mapper, null);
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
        int inserted = mapper.insertTask(task);
        if (inserted <= 0 || task.getId() == null) {
            throw new IllegalStateException("excel task insert failed");
        }
        return task.getId();
    }

    public void createError(Long taskId, int rowIndex, String errorMessage, String rawData) {
        int inserted = mapper.insertError(taskId, rowIndex, errorMessage, rawData);
        if (inserted <= 0) {
            throw new IllegalStateException("excel error insert failed");
        }
    }

    public Long createTaskWithErrors(ExcelAdminModels.Task task, List<ExcelAdminModels.ErrorRecord> errors) {
        return inTransaction(() -> {
            Long taskId = createTask(task);
            if (errors != null) {
                for (ExcelAdminModels.ErrorRecord error : errors) {
                    createError(taskId, rowIndex(error), error.getErrorMessage(), error.getRawData());
                }
            }
            return taskId;
        });
    }

    public List<ExcelAdminModels.ErrorRecord> listErrors(Long taskId) {
        return mapper.listErrors(taskId);
    }

    private static int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * pageSize;
    }

    private static String text(String value) {
        String text = AdminTextSupport.trimToNull(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private <T> T inTransaction(Supplier<T> action) {
        if (transactionTemplate == null) {
            return action.get();
        }
        return transactionTemplate.execute(status -> action.get());
    }

    private static int rowIndex(ExcelAdminModels.ErrorRecord error) {
        return error.getRowIndex() == null ? 0 : error.getRowIndex();
    }

}
