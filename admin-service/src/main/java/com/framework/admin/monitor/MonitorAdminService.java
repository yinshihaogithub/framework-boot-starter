package com.framework.admin.monitor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 监控中心聚合服务。
 */
@Slf4j
@Service
public class MonitorAdminService {

    private final ObjectProvider<HealthEndpoint> healthEndpointProvider;

    public MonitorAdminService(ObjectProvider<HealthEndpoint> healthEndpointProvider) {
        this.healthEndpointProvider = healthEndpointProvider;
    }

    public Object health() {
        try {
            HealthEndpoint endpoint = healthEndpointProvider.getIfAvailable();
            HealthComponent health = endpoint == null ? null : endpoint.health();
            return health == null ? Map.of("status", "UNKNOWN") : health;
        } catch (RuntimeException e) {
            log.warn("[监控中心] 健康检查依赖不可用 error={}", e.getMessage());
            return Map.of("status", "UNKNOWN");
        }
    }

    public Map<String, Object> jvm() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("availableProcessors", runtime.availableProcessors());
        data.put("maxMemory", runtime.maxMemory());
        data.put("totalMemory", runtime.totalMemory());
        data.put("freeMemory", runtime.freeMemory());
        data.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        data.put("javaVersion", System.getProperty("java.version"));
        return data;
    }
}
