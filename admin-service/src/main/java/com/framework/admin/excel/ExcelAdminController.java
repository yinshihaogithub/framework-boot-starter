package com.framework.admin.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.excel.service.ExcelExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/excel")
@Tag(name = "Excel中心", description = "Excel 导入导出任务和错误明细")
public class ExcelAdminController {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ExcelAdminRepository repository;
    private final ObjectProvider<ExcelExportService> exportServiceProvider;
    private final AdminAuditService auditService;

    public ExcelAdminController(ExcelAdminRepository repository,
                                ObjectProvider<ExcelExportService> exportServiceProvider,
                                AdminAuditService auditService) {
        this.repository = repository;
        this.exportServiceProvider = exportServiceProvider;
        this.auditService = auditService;
    }

    @Operation(summary = "Excel任务统计")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.success(repository.stats());
    }

    @Operation(summary = "Excel任务列表")
    @GetMapping("/tasks")
    public Result<PageResult<ExcelAdminModels.Task>> tasks(@RequestParam(required = false) String taskType,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") int pageNum,
                                                           @RequestParam(defaultValue = "20") int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<ExcelAdminModels.Task> records = repository.listTasks(taskType, status, safePageNum, safePageSize);
        long total = repository.countTasks(taskType, status);
        return Result.success(PageResult.of(records, total, safePageNum, safePageSize));
    }

    @Operation(summary = "创建示例导出任务")
    @PostMapping("/tasks/demo-export")
    public Result<ExcelAdminModels.TaskResult> demoExport(@RequestBody(required = false) ExcelAdminModels.ExportRequest request,
                                                         HttpServletRequest servletRequest) {
        ExcelExportService exportService = exportServiceProvider.getIfAvailable();
        if (exportService == null) {
            return Result.fail("Excel导出服务未启用");
        }
        List<DemoExcelRow> rows = List.of(
                new DemoExcelRow("admin", "系统管理员", "ENABLED"),
                new DemoExcelRow("ops", "运维人员", "ENABLED"));
        byte[] bytes = exportService.export("用户示例", DemoExcelRow.class, rows);
        String filename = "demo-export-" + System.currentTimeMillis() + ".xlsx";
        ExcelAdminModels.Task task = new ExcelAdminModels.Task()
                .setTaskName(text(request == null ? null : request.getTaskName(), "示例导出任务"))
                .setTaskType("EXPORT")
                .setBizType(text(request == null ? null : request.getBizType(), "demo-user"))
                .setStatus("SUCCESS")
                .setFilename(filename)
                .setTotalRows(rows.size())
                .setSuccessRows(rows.size())
                .setFailureRows(0)
                .setOperatorName(UserContextHolder.getUsername());
        Long taskId = repository.createTask(task);
        auditService.success(servletRequest, "Excel中心", "创建示例导出任务", "CREATE",
                auditService.params("taskId", taskId, "filename", filename, "rows", rows.size()));
        return Result.success(new ExcelAdminModels.TaskResult()
                .setTaskId(taskId)
                .setFilename(filename)
                .setStatus("SUCCESS")
                .setTotalRows(rows.size())
                .setSuccessRows(rows.size())
                .setFailureRows(0)
                .setFileSize((long) bytes.length));
    }

    @Operation(summary = "创建示例失败任务")
    @PostMapping("/tasks/demo-failure")
    public Result<ExcelAdminModels.TaskResult> demoFailure(@RequestBody(required = false) ExcelAdminModels.FailureRequest request,
                                                          HttpServletRequest servletRequest) {
        String errorMessage = text(request == null ? null : request.getErrorMessage(), "模板表头不匹配");
        ExcelAdminModels.Task task = new ExcelAdminModels.Task()
                .setTaskName(text(request == null ? null : request.getTaskName(), "示例导入失败任务"))
                .setTaskType("IMPORT")
                .setBizType(text(request == null ? null : request.getBizType(), "demo-user"))
                .setStatus("FAILED")
                .setFilename("demo-import-" + System.currentTimeMillis() + ".xlsx")
                .setTotalRows(3)
                .setSuccessRows(1)
                .setFailureRows(2)
                .setOperatorName(UserContextHolder.getUsername())
                .setErrorMessage(errorMessage);
        Long taskId = repository.createTask(task);
        repository.createError(taskId, 2, errorMessage, "{\"username\":\"\",\"nickname\":\"空用户名\"}");
        repository.createError(taskId, 3, "手机号格式错误", "{\"username\":\"demo\",\"mobile\":\"123\"}");
        auditService.success(servletRequest, "Excel中心", "创建示例失败任务", "CREATE",
                auditService.params("taskId", taskId, "errorMessage", errorMessage));
        return Result.success(new ExcelAdminModels.TaskResult()
                .setTaskId(taskId)
                .setFilename(task.getFilename())
                .setStatus("FAILED")
                .setTotalRows(3)
                .setSuccessRows(1)
                .setFailureRows(2)
                .setFileSize(0L));
    }

    @Operation(summary = "Excel错误明细")
    @GetMapping("/tasks/{taskId}/errors")
    public Result<List<ExcelAdminModels.ErrorRecord>> errors(@PathVariable Long taskId) {
        return Result.success(repository.listErrors(taskId));
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DemoExcelRow {
        @ExcelProperty("用户名")
        private String username;

        @ExcelProperty("昵称")
        private String nickname;

        @ExcelProperty("状态")
        private String status;
    }
}
