package com.framework.admin.log;

import com.framework.admin.system.AdminSystemModels.LoginLog;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.log.entity.OperationLogEntity;
import com.framework.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 日志审计后台接口。
 */
@RestController
@RequestMapping("/admin/logs")
@Tag(name = "日志审计", description = "操作日志、API日志和 trace 查询")
public class LogAdminController {

    private final LogAdminService logAdminService;

    public LogAdminController(LogAdminService logAdminService) {
        this.logAdminService = logAdminService;
    }

    @Operation(summary = "日志统计")
    @GetMapping("/stats")
    @RequirePermission("log:view")
    public Result<Map<String, Long>> stats() {
        return Result.success(logAdminService.stats());
    }

    @Operation(summary = "日志列表")
    @GetMapping
    @RequirePermission("log:view")
    public Result<PageResult<OperationLogEntity>> list(@RequestParam(required = false) String module,
                                                       @RequestParam(required = false) String logType,
                                                       @RequestParam(required = false) Long operatorId,
                                                       @RequestParam(required = false) Boolean success,
                                                       @RequestParam(required = false) String traceId,
                                                       @RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(logAdminService.list(module, logType, operatorId, success, traceId, pageNum, pageSize));
    }

    @Operation(summary = "按 traceId 查询链路日志")
    @GetMapping("/traces/{traceId}")
    @RequirePermission("trace:view")
    public Result<PageResult<OperationLogEntity>> trace(@PathVariable String traceId,
                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "50") int pageSize) {
        if (traceId == null || traceId.isBlank()) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "traceId 不能为空");
        }
        return Result.success(logAdminService.trace(traceId.trim(), pageNum, pageSize));
    }

    @Operation(summary = "登录日志列表")
    @GetMapping("/login")
    @RequirePermission("log:login:view")
    public Result<PageResult<LoginLog>> loginLogs(@RequestParam(required = false) String username,
                                                  @RequestParam(required = false) Boolean success,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(logAdminService.loginLogs(username, success, pageNum, pageSize));
    }
}
