package com.framework.admin.dashboard;

import com.framework.core.result.Result;
import com.framework.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理后台首页聚合数据。
 */
@RestController
@RequestMapping("/admin/dashboard")
@Tag(name = "Dashboard", description = "管理后台首页")
@RequirePermission("dashboard:view")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "管理后台首页统计")
    @GetMapping
    public Result<DashboardSummary> summary() {
        return Result.success(dashboardService.summary());
    }

    public record DashboardSummary(Map<String, Long> mq,
                                   Map<String, Long> localMessage,
                                   Map<String, Long> logs,
                                   List<ModuleStatus> modules) {
    }

    public record ModuleStatus(String name, String status) {
    }
}
