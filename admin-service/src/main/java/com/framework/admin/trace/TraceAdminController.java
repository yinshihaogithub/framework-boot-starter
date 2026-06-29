package com.framework.admin.trace;

import com.framework.admin.trace.TraceAdminModels.TraceDetail;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/traces")
@Tag(name = "链路追踪", description = "按 traceId 聚合 HTTP 日志、MQ 失败消息和本地消息")
@RequirePermission("trace:view")
public class TraceAdminController {

    private final TraceAdminService traceAdminService;

    public TraceAdminController(TraceAdminService traceAdminService) {
        this.traceAdminService = traceAdminService;
    }

    @Operation(summary = "traceId 链路详情")
    @GetMapping("/{traceId}")
    public Result<TraceDetail> detail(@PathVariable String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "traceId 不能为空");
        }
        return Result.success(traceAdminService.detail(traceId.trim()));
    }
}
