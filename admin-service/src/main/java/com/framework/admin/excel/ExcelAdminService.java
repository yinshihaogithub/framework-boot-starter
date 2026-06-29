package com.framework.admin.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
import com.framework.core.result.ResultCode;
import com.framework.excel.service.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class ExcelAdminService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ExcelAdminRepository repository;
    private final ObjectProvider<ExcelExportService> exportServiceProvider;
    private final AdminAuditService auditService;

    public ExcelAdminService(ExcelAdminRepository repository,
                             ObjectProvider<ExcelExportService> exportServiceProvider,
                             AdminAuditService auditService) {
        this.repository = repository;
        this.exportServiceProvider = exportServiceProvider;
        this.auditService = auditService;
    }

    public Map<String, Long> stats() {
        try {
            return repository.stats();
        } catch (Exception ignored) {
            return zeroStats();
        }
    }

    public PageResult<ExcelAdminModels.Task> tasks(String taskType, String status, int pageNum, int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        String normalizedTaskType = normalize(taskType);
        String normalizedStatus = normalize(status);
        try {
            List<ExcelAdminModels.Task> records = repository.listTasks(normalizedTaskType, normalizedStatus,
                    safePageNum, safePageSize);
            long total = repository.countTasks(normalizedTaskType, normalizedStatus);
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
                    .setOperatorName(UserContextHolder.getUsername())
                    .setErrorMessage(errorMessage);
            try {
                Long taskId = repository.createTask(failedTask);
                auditService.failure(servletRequest, "Excel中心", "创建导出任务", "CREATE",
                        auditService.params("taskId", taskId, "filename", filename, "rows", rows.size()),
                        e);
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
                .setOperatorName(UserContextHolder.getUsername());
        try {
            Long taskId = repository.createTask(task);
            auditService.success(servletRequest, "Excel中心", "创建导出任务", "CREATE",
                    auditService.params("taskId", taskId, "filename", filename, "rows", rows.size()));
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
        ExcelAdminModels.Task task = new ExcelAdminModels.Task()
                .setTaskName(text(request == null ? null : request.getTaskName(), "用户导入失败任务"))
                .setTaskType("IMPORT")
                .setBizType(text(request == null ? null : request.getBizType(), "system-user"))
                .setStatus("FAILED")
                .setFilename("user-import-" + System.currentTimeMillis() + ".xlsx")
                .setTotalRows(3)
                .setSuccessRows(1)
                .setFailureRows(2)
                .setOperatorName(UserContextHolder.getUsername())
                .setErrorMessage(errorMessage);
        try {
            Long taskId = repository.createTask(task);
            repository.createError(taskId, 2, errorMessage, "{\"username\":\"\",\"nickname\":\"空用户名\"}");
            repository.createError(taskId, 3, "手机号格式错误", "{\"username\":\"ops\",\"mobile\":\"123\"}");
            auditService.success(servletRequest, "Excel中心", "登记导入失败任务", "CREATE",
                    auditService.params("taskId", taskId, "errorMessage", errorMessage));
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
        try {
            return repository.listErrors(taskId);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private int safePageNum(int pageNum) {
        return pageNum > 0 ? pageNum : DEFAULT_PAGE_NUM;
    }

    private int safePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private Map<String, Long> zeroStats() {
        return Map.of("total", 0L, "success", 0L, "failed", 0L, "import", 0L, "export", 0L);
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
