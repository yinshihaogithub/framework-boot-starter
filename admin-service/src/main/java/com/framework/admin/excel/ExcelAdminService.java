package com.framework.admin.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.framework.admin.audit.AdminAuditService;
import com.framework.admin.support.AdminPageSupport;
import com.framework.admin.support.AdminTextSupport;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
import com.framework.core.result.ResultCode;
import com.framework.excel.service.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.Set;

@Slf4j
@Service
public class ExcelAdminService {

    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of("IMPORT", "EXPORT");
    private static final Set<String> SUPPORTED_STATUSES = Set.of("SUCCESS", "FAILED", "PROCESSING");

    private final ExcelAdminMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final ObjectProvider<ExcelExportService> exportServiceProvider;
    private final AdminAuditService auditService;

    @Autowired
    public ExcelAdminService(ExcelAdminMapper mapper,
                             TransactionTemplate transactionTemplate,
                             ObjectProvider<ExcelExportService> exportServiceProvider,
                             AdminAuditService auditService) {
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
        this.exportServiceProvider = exportServiceProvider;
        this.auditService = auditService;
    }

    public ExcelAdminService(ExcelAdminMapper mapper,
                             ObjectProvider<ExcelExportService> exportServiceProvider,
                             AdminAuditService auditService) {
        this(mapper, null, exportServiceProvider, auditService);
    }

    public Map<String, Long> stats() {
        try {
            return ExcelAdminMapperSupport.stats(mapper);
        } catch (Exception ignored) {
            return zeroStats();
        }
    }

    public PageResult<ExcelAdminModels.Task> tasks(String taskType, String status, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String normalizedTaskType = normalizeTaskTypeFilter(taskType);
        String normalizedStatus = normalizeStatusFilter(status);
        if (isInvalidFilter(taskType, normalizedTaskType) || isInvalidFilter(status, normalizedStatus)) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        try {
            List<ExcelAdminModels.Task> records = ExcelAdminMapperSupport.listTasks(mapper,
                    normalizedTaskType, normalizedStatus, safePageNum, safePageSize);
            long total = ExcelAdminMapperSupport.countTasks(mapper, normalizedTaskType, normalizedStatus);
            return PageResult.of(records, total, safePageNum, safePageSize);
        } catch (Exception ignored) {
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public ActionResult<ExcelAdminModels.TaskResult> createExportTask(ExcelAdminModels.ExportRequest request,
                                                                      HttpServletRequest servletRequest) {
        ExcelExportService exportService = available(exportServiceProvider);
        if (exportService == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "Excel导出服务未启用");
        }
        List<ExportUserRow> rows = List.of(
                new ExportUserRow("admin", "系统管理员", "ENABLED"),
                new ExportUserRow("ops", "运维人员", "ENABLED"));
        String filename = "user-export-" + System.currentTimeMillis() + ".xlsx";
        String taskName = text(request == null ? null : request.getTaskName(), "用户清单导出任务");
        String bizType = text(request == null ? null : request.getBizType(), "system-user");
        String operatorName = currentOperatorName();
        byte[] bytes;
        try {
            bytes = exportService.export("用户清单", ExportUserRow.class, rows);
        } catch (RuntimeException e) {
            String errorMessage = errorMessage(e);
            ExcelAdminModels.Task failedTask = new ExcelAdminModels.Task()
                    .setTaskName(taskName)
                    .setTaskType("EXPORT")
                    .setBizType(bizType)
                    .setStatus("FAILED")
                    .setFilename(filename)
                    .setTotalRows(rows.size())
                    .setSuccessRows(0)
                    .setFailureRows(rows.size())
                    .setOperatorName(operatorName)
                    .setErrorMessage(errorMessage);
            try {
                Long taskId = ExcelAdminMapperSupport.createTask(mapper, failedTask);
                auditFailure(servletRequest, "创建导出任务", "CREATE", e,
                        "taskId", taskId, "filename", filename, "rows", rows.size(), "operator", operatorName);
                return ActionResult.success(new ExcelAdminModels.TaskResult()
                        .setTaskId(taskId)
                        .setFilename(filename)
                        .setStatus("FAILED")
                        .setTotalRows(rows.size())
                        .setSuccessRows(0)
                        .setFailureRows(rows.size())
                        .setFileSize(0L));
            } catch (RuntimeException saveFailure) {
                log.warn("[Excel中心] 记录导出失败任务失败 filename={}, error={}",
                        filename, saveFailure.getMessage());
                return ActionResult.fail(ResultCode.SERVICE_ERROR, "Excel导出任务创建失败");
            }
        }
        ExcelAdminModels.Task task = new ExcelAdminModels.Task()
                .setTaskName(taskName)
                .setTaskType("EXPORT")
                .setBizType(bizType)
                .setStatus("SUCCESS")
                .setFilename(filename)
                .setTotalRows(rows.size())
                .setSuccessRows(rows.size())
                .setFailureRows(0)
                .setOperatorName(operatorName);
        try {
            Long taskId = ExcelAdminMapperSupport.createTask(mapper, task);
            auditSuccess(servletRequest, "创建导出任务", "CREATE",
                    "taskId", taskId, "filename", filename, "rows", rows.size(), "operator", operatorName);
            return ActionResult.success(new ExcelAdminModels.TaskResult()
                    .setTaskId(taskId)
                    .setFilename(filename)
                    .setStatus("SUCCESS")
                    .setTotalRows(rows.size())
                    .setSuccessRows(rows.size())
                    .setFailureRows(0)
                    .setFileSize((long) bytes.length));
        } catch (RuntimeException e) {
            log.warn("[Excel中心] 创建导出任务失败 filename={}, error={}", filename, e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "Excel导出任务创建失败");
        }
    }

    public ActionResult<ExcelAdminModels.TaskResult> createImportFailureTask(ExcelAdminModels.FailureRequest request,
                                                                            HttpServletRequest servletRequest) {
        String errorMessage = text(request == null ? null : request.getErrorMessage(), "模板表头不匹配");
        String operatorName = currentOperatorName();
        ExcelAdminModels.Task task = new ExcelAdminModels.Task()
                .setTaskName(text(request == null ? null : request.getTaskName(), "用户导入失败任务"))
                .setTaskType("IMPORT")
                .setBizType(text(request == null ? null : request.getBizType(), "system-user"))
                .setStatus("FAILED")
                .setFilename("user-import-" + System.currentTimeMillis() + ".xlsx")
                .setTotalRows(3)
                .setSuccessRows(1)
                .setFailureRows(2)
                .setOperatorName(operatorName)
                .setErrorMessage(errorMessage);
        try {
            Long taskId = inTransaction(() -> ExcelAdminMapperSupport.createTaskWithErrors(mapper, task, List.of(
                    new ExcelAdminModels.ErrorRecord()
                            .setRowIndex(2)
                            .setErrorMessage(errorMessage)
                            .setRawData("{\"username\":\"\",\"nickname\":\"空用户名\"}"),
                    new ExcelAdminModels.ErrorRecord()
                            .setRowIndex(3)
                            .setErrorMessage(errorMessage)
                            .setRawData("{\"username\":\"ops\",\"mobile\":\"123\"}")
            )));
            auditSuccess(servletRequest, "登记导入失败任务", "CREATE",
                    "taskId", taskId, "errorMessage", errorMessage, "operator", operatorName);
            return ActionResult.success(new ExcelAdminModels.TaskResult()
                    .setTaskId(taskId)
                    .setFilename(task.getFilename())
                    .setStatus("FAILED")
                    .setTotalRows(3)
                    .setSuccessRows(1)
                    .setFailureRows(2)
                    .setFileSize(0L));
        } catch (RuntimeException e) {
            log.warn("[Excel中心] 登记导入失败任务失败 error={}", e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "Excel导入失败任务登记失败");
        }
    }

    public List<ExcelAdminModels.ErrorRecord> errors(Long taskId) {
        if (taskId == null || taskId <= 0) {
            return List.of();
        }
        try {
            return ExcelAdminMapperSupport.listErrors(mapper, taskId);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String text(String value, String fallback) {
        String text = AdminTextSupport.trimToNull(value);
        return text == null ? fallback : text;
    }

    private String currentOperatorName() {
        return text(UserContextHolder.getUsername(), "admin");
    }

    private String normalize(String value) {
        String text = AdminTextSupport.trimToNull(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private String normalizeTaskTypeFilter(String taskType) {
        String normalizedTaskType = normalize(taskType);
        return normalizedTaskType == null || SUPPORTED_TASK_TYPES.contains(normalizedTaskType)
                ? normalizedTaskType
                : null;
    }

    private String normalizeStatusFilter(String status) {
        String normalizedStatus = normalize(status);
        return normalizedStatus == null || SUPPORTED_STATUSES.contains(normalizedStatus)
                ? normalizedStatus
                : null;
    }

    private boolean isInvalidFilter(String originalValue, String normalizedValue) {
        return AdminTextSupport.hasText(originalValue) && normalizedValue == null;
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        String text = AdminTextSupport.trimToNull(message);
        return text == null ? exception.getClass().getSimpleName() : text;
    }

    private void auditSuccess(HttpServletRequest request, String action, String operationType, Object... params) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.success(request, "Excel中心", action, operationType, auditService.params(params));
        } catch (RuntimeException e) {
            log.warn("[Excel中心] 审计日志写入失败 action={}, error={}", action, e.getMessage());
        }
    }

    private void auditFailure(HttpServletRequest request, String action, String operationType,
                              Exception exception, Object... params) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.failure(request, "Excel中心", action, operationType, auditService.params(params), exception);
        } catch (RuntimeException e) {
            log.warn("[Excel中心] 审计日志写入失败 action={}, error={}", action, e.getMessage());
        }
    }

    private Map<String, Long> zeroStats() {
        return Map.of("total", 0L, "success", 0L, "failed", 0L, "import", 0L, "export", 0L);
    }

    private <T> T inTransaction(Supplier<T> action) {
        if (transactionTemplate == null) {
            return action.get();
        }
        return transactionTemplate.execute(status -> action.get());
    }

    private <T> T available(ObjectProvider<T> provider) {
        if (provider == null) {
            return null;
        }
        try {
            return provider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("[Excel中心] 获取导出服务失败 error={}", e.getMessage());
            return null;
        }
    }

    public record ActionResult<T>(boolean success, int code, String message, T data) {

        public static <T> ActionResult<T> success(T data) {
            return new ActionResult<>(true, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
        }

        public static <T> ActionResult<T> fail(ResultCode code, String message) {
            return new ActionResult<>(false, code.getCode(), message, null);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportUserRow {
        @ExcelProperty("用户名")
        private String username;

        @ExcelProperty("昵称")
        private String nickname;

        @ExcelProperty("状态")
        private String status;
    }
}
