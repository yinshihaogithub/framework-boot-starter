package com.framework.monitor.health;

import com.framework.monitor.config.MonitorProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic framework health indicator.
 */
public class FrameworkHealthIndicator implements HealthIndicator {

    private static final String DEFAULT_APPLICATION_NAME = "framework-application";
    private static final Map<String, String> MODULE_MARKERS = moduleMarkers();

    private final MonitorProperties properties;

    public FrameworkHealthIndicator(MonitorProperties properties) {
        this.properties = properties;
        properties.validate();
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("application", applicationName())
                .withDetail("framework", "ready")
                .withDetail("frameworkVersion", frameworkVersion())
                .withDetail("modules", availableModules())
                .withDetail("javaVersion", System.getProperty("java.version"))
                .withDetail("os", System.getProperty("os.name"))
                .build();
    }

    private String applicationName() {
        String applicationName = properties.getApplicationName();
        return applicationName == null || applicationName.isBlank()
                ? DEFAULT_APPLICATION_NAME
                : applicationName.trim();
    }

    private String frameworkVersion() {
        String version = FrameworkHealthIndicator.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "unknown" : version;
    }

    private List<String> availableModules() {
        ClassLoader classLoader = FrameworkHealthIndicator.class.getClassLoader();
        return MODULE_MARKERS.entrySet().stream()
                .filter(entry -> ClassUtils.isPresent(entry.getValue(), classLoader))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Map<String, String> moduleMarkers() {
        Map<String, String> markers = new LinkedHashMap<>();
        markers.put("framework-core", "com.framework.core.trace.TraceContext");
        markers.put("framework-web", "com.framework.web.config.TraceIdFilter");
        markers.put("framework-auth", "com.framework.auth.context.UserContextHolder");
        markers.put("framework-security", "com.framework.security.aspect.PermissionAspect");
        markers.put("framework-cache", "com.framework.cache.service.CacheService");
        markers.put("framework-lock", "com.framework.lock.aspect.DistributedLockAspect");
        markers.put("framework-idempotent", "com.framework.idempotent.aspect.IdempotentAspect");
        markers.put("framework-crypto", "com.framework.crypto.util.AesUtils");
        markers.put("framework-log", "com.framework.log.aspect.OperationLogAspect");
        markers.put("framework-rate-limiter", "com.framework.ratelimiter.aspect.RateLimitAspect");
        markers.put("framework-mq", "com.framework.mq.config.MqAutoConfiguration");
        markers.put("framework-retry", "com.framework.retry.aspect.RetryAspect");
        markers.put("framework-tools", "com.framework.tools.date.DateUtils");
        markers.put("framework-notify", "com.framework.notify.service.NotifyService");
        markers.put("framework-local-message", "com.framework.localmessage.service.LocalMessageService");
        markers.put("framework-excel", "com.framework.excel.service.ExcelImportService");
        markers.put("framework-datasource", "com.framework.datasource.config.DatasourceAutoConfiguration");
        markers.put("framework-redis", "com.framework.redis.service.RedisService");
        markers.put("framework-feign", "com.framework.feign.config.FrameworkFeignRequestInterceptor");
        markers.put("framework-monitor", "com.framework.monitor.health.FrameworkHealthIndicator");
        markers.put("framework-job", "com.framework.job.service.JobHandler");
        markers.put("framework-file", "com.framework.file.service.FileStorageService");
        return markers;
    }
}
