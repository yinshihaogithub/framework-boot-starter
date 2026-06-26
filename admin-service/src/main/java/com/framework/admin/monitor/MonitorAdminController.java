package com.framework.admin.monitor;

import com.framework.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 监控中心后台接口。
 */
@RestController
@RequestMapping("/admin/monitor")
@Tag(name = "监控中心", description = "健康检查和运行时指标")
public class MonitorAdminController {

    private final ObjectProvider<HealthEndpoint> healthEndpointProvider;

    public MonitorAdminController(ObjectProvider<HealthEndpoint> healthEndpointProvider) {
        this.healthEndpointProvider = healthEndpointProvider;
    }

    @Operation(summary = "服务健康")
    @GetMapping("/health")
    public Result<Object> health() {
        HealthEndpoint endpoint = healthEndpointProvider.getIfAvailable();
        HealthComponent health = endpoint == null ? null : endpoint.health();
        return Result.success(health == null ? Map.of("status", "UNKNOWN") : health);
    }

    @Operation(summary = "JVM概览")
    @GetMapping("/jvm")
    public Result<Map<String, Object>> jvm() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("availableProcessors", runtime.availableProcessors());
        data.put("maxMemory", runtime.maxMemory());
        data.put("totalMemory", runtime.totalMemory());
        data.put("freeMemory", runtime.freeMemory());
        data.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        data.put("javaVersion", System.getProperty("java.version"));
        return Result.success(data);
    }
}
