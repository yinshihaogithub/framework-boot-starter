package com.framework.core.module;

import org.springframework.util.ClassUtils;

import java.util.List;

/**
 * Known framework modules and their lightweight classpath markers.
 */
public final class FrameworkModuleRegistry {

    private static final List<ModuleMarker> DEFAULT_MODULES = List.of(
            module("framework-core", "com.framework.core.trace.TraceContext"),
            module("framework-web", "com.framework.web.config.TraceIdFilter"),
            module("framework-auth", "com.framework.auth.context.UserContextHolder"),
            module("framework-security", "com.framework.security.aspect.PermissionAspect"),
            module("framework-cache", "com.framework.cache.service.CacheService"),
            module("framework-lock", "com.framework.lock.aspect.DistributedLockAspect"),
            module("framework-idempotent", "com.framework.idempotent.aspect.IdempotentAspect"),
            module("framework-crypto", "com.framework.crypto.util.AesUtils"),
            module("framework-log", "com.framework.log.aspect.OperationLogAspect"),
            module("framework-rate-limiter", "com.framework.ratelimiter.aspect.RateLimitAspect"),
            module("framework-mq", "com.framework.mq.config.MqAutoConfiguration"),
            module("framework-retry", "com.framework.retry.aspect.RetryAspect"),
            module("framework-tools", "com.framework.tools.date.DateUtils"),
            module("framework-notify", "com.framework.notify.service.NotifyService"),
            module("framework-local-message", "com.framework.localmessage.service.LocalMessageService"),
            module("framework-excel", "com.framework.excel.service.ExcelImportService"),
            module("framework-datasource", "com.framework.datasource.config.DatasourceAutoConfiguration"),
            module("framework-redis", "com.framework.redis.service.RedisService"),
            module("framework-feign", "com.framework.feign.config.FrameworkFeignRequestInterceptor"),
            module("framework-monitor", "com.framework.monitor.health.FrameworkHealthIndicator"),
            module("framework-job", "com.framework.job.service.JobHandler"),
            module("framework-file", "com.framework.file.service.FileStorageService")
    );

    private FrameworkModuleRegistry() {
    }

    public static List<ModuleMarker> defaultModules() {
        return DEFAULT_MODULES;
    }

    public static List<String> availableModuleNames(ClassLoader classLoader) {
        return DEFAULT_MODULES.stream()
                .filter(module -> module.isPresent(classLoader))
                .map(ModuleMarker::name)
                .toList();
    }

    private static ModuleMarker module(String name, String markerClass) {
        return new ModuleMarker(name, markerClass);
    }

    public record ModuleMarker(String name, String markerClass) {

        public boolean isPresent(ClassLoader classLoader) {
            return ClassUtils.isPresent(markerClass, classLoader);
        }
    }
}
