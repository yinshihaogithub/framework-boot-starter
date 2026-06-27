package com.framework.admin.excel;

import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/excel")
@Tag(name = "Excel中心", description = "Excel 导入导出任务和错误明细")
public class ExcelAdminController {

    private final ExcelAdminService excelAdminService;

    public ExcelAdminController(ExcelAdminService excelAdminService) {
        this.excelAdminService = excelAdminService;
    }

    @Operation(summary = "Excel任务统计")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.success(excelAdminService.stats());
    }

    @Operation(summary = "Excel任务列表")
    @GetMapping("/tasks")
    public Result<PageResult<ExcelAdminModels.Task>> tasks(@RequestParam(required = false) String taskType,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") int pageNum,
                                                           @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(excelAdminService.tasks(taskType, status, pageNum, pageSize));
    }

    @Operation(summary = "创建示例导出任务")
    @PostMapping("/tasks/demo-export")
    public Result<ExcelAdminModels.TaskResult> demoExport(@RequestBody(required = false) ExcelAdminModels.ExportRequest request,
                                                         HttpServletRequest servletRequest) {
        return excelAdminService.demoExport(request, servletRequest)
                .map(Result::success)
                .orElseGet(() -> Result.fail("Excel导出服务未启用"));
    }

    @Operation(summary = "创建示例失败任务")
    @PostMapping("/tasks/demo-failure")
    public Result<ExcelAdminModels.TaskResult> demoFailure(@RequestBody(required = false) ExcelAdminModels.FailureRequest request,
                                                          HttpServletRequest servletRequest) {
        return Result.success(excelAdminService.demoFailure(request, servletRequest));
    }

    @Operation(summary = "Excel错误明细")
    @GetMapping("/tasks/{taskId}/errors")
    public Result<List<ExcelAdminModels.ErrorRecord>> errors(@PathVariable Long taskId) {
        return Result.success(excelAdminService.errors(taskId));
    }
}
