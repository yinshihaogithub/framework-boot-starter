package com.framework.admin.monitor;

import com.framework.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 监控中心后台接口。
 */
@RestController
@RequestMapping("/admin/monitor")
@Tag(name = "监控中心", description = "健康检查和运行时指标")
public class MonitorAdminController {

    private final MonitorAdminService monitorAdminService;

    public MonitorAdminController(MonitorAdminService monitorAdminService) {
        this.monitorAdminService = monitorAdminService;
    }

    @Operation(summary = "服务健康")
    @GetMapping("/health")
    public Result<Object> health() {
        return Result.success(monitorAdminService.health());
    }

    @Operation(summary = "JVM概览")
    @GetMapping("/jvm")
    public Result<Map<String, Object>> jvm() {
        return Result.success(monitorAdminService.jvm());
    }
}
