package com.framework.admin.dashboard;

import com.framework.admin.excel.ExcelAdminMapper;
import com.framework.admin.file.FileAdminRepository;
import com.framework.admin.notify.NotifyAdminMapper;
import com.framework.admin.system.AdminSystemModels.ConfigItem;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.service.LocalMessageService;
import com.framework.log.mapper.OperationLogMapper;
import com.framework.mq.deadletter.DeadLetterHandler;
import com.framework.mq.deadletter.MqFailedMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台首页聚合服务。
 */
@Service
public class DashboardService {

    private final ObjectProvider<DeadLetterHandler> deadLetterHandlerProvider;
    private final ObjectProvider<LocalMessageService> localMessageServiceProvider;
    private final ObjectProvider<OperationLogMapper> operationLogMapperProvider;
    private final ObjectProvider<NotifyAdminMapper> notifyAdminMapperProvider;
    private final ObjectProvider<ExcelAdminMapper> excelAdminMapperProvider;
    private final ObjectProvider<FileAdminRepository> fileAdminRepositoryProvider;
    private final ObjectProvider<AdminSystemRepository> adminSystemRepositoryProvider;

    public DashboardService(ObjectProvider<DeadLetterHandler> deadLetterHandlerProvider,
                            ObjectProvider<LocalMessageService> localMessageServiceProvider,
                            ObjectProvider<OperationLogMapper> operationLogMapperProvider,
                            ObjectProvider<NotifyAdminMapper> notifyAdminMapperProvider,
                            ObjectProvider<ExcelAdminMapper> excelAdminMapperProvider,
                            ObjectProvider<FileAdminRepository> fileAdminRepositoryProvider,
                            ObjectProvider<AdminSystemRepository> adminSystemRepositoryProvider) {
        this.deadLetterHandlerProvider = deadLetterHandlerProvider;
        this.localMessageServiceProvider = localMessageServiceProvider;
        this.operationLogMapperProvider = operationLogMapperProvider;
        this.notifyAdminMapperProvider = notifyAdminMapperProvider;
        this.excelAdminMapperProvider = excelAdminMapperProvider;
        this.fileAdminRepositoryProvider = fileAdminRepositoryProvider;
        this.adminSystemRepositoryProvider = adminSystemRepositoryProvider;
    }

    public DashboardController.DashboardSummary summary() {
        return new DashboardController.DashboardSummary(
                mqMetrics(),
                localMessageMetrics(),
                logMetrics(),
                notifyMetrics(),
                excelMetrics(),
                fileMetrics(),
                securityStatus(),
                moduleStatuses());
    }

    private Map<String, Long> mqMetrics() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("pending", 0L);
        metrics.put("retrying", 0L);
        metrics.put("manual", 0L);
        metrics.put("exhausted", 0L);
        metrics.put("total", 0L);
        DeadLetterHandler handler = available(deadLetterHandlerProvider);
        if (handler == null) {
            return metrics;
        }
        try {
            var store = handler.getFailedMessageStore();
            metrics.put("pending", countMq(store, MqFailedMessage.STATUS_PENDING));
            metrics.put("retrying", countMq(store, MqFailedMessage.STATUS_RETRYING));
            metrics.put("manual", countMq(store, MqFailedMessage.STATUS_MANUAL));
            metrics.put("exhausted", countMq(store, MqFailedMessage.STATUS_EXHAUSTED));
            metrics.put("total", (long) store.size());
        } catch (Exception ignored) {
            return zero(metrics);
        }
        return metrics;
    }

    private long countMq(Map<Long, MqFailedMessage> store, String status) {
        return store.values().stream()
                .filter(message -> status.equals(message.getStatus()))
                .count();
    }

    private Map<String, Long> localMessageMetrics() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        for (LocalMessageStatus status : LocalMessageStatus.values()) {
            metrics.put(status.name().toLowerCase(), 0L);
        }
        metrics.put("total", 0L);
        LocalMessageService service = available(localMessageServiceProvider);
        if (service == null) {
            return metrics;
        }
        try {
            var messages = service.findAll();
            for (LocalMessageStatus status : LocalMessageStatus.values()) {
                long count = messages.stream()
                        .filter(message -> status == message.getStatus())
                        .count();
                metrics.put(status.name().toLowerCase(), count);
            }
            metrics.put("total", (long) messages.size());
        } catch (Exception ignored) {
            return zero(metrics);
        }
        return metrics;
    }

    private Map<String, Long> logMetrics() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("total", 0L);
        OperationLogMapper mapper = available(operationLogMapperProvider);
        if (mapper == null) {
            return metrics;
        }
        try {
            metrics.put("total", mapper.count(null, null, null, null, null));
        } catch (Exception ignored) {
            metrics.put("total", 0L);
        }
        return metrics;
    }

    private Map<String, Long> notifyMetrics() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("templates", 0L);
        metrics.put("enabledTemplates", 0L);
        metrics.put("records", 0L);
        metrics.put("successRecords", 0L);
        metrics.put("failedRecords", 0L);
        NotifyAdminMapper mapper = available(notifyAdminMapperProvider);
        if (mapper == null) {
            return metrics;
        }
        try {
            metrics.put("templates", mapper.countTemplates(null, null, null));
            metrics.put("enabledTemplates", mapper.countTemplatesByStatus("ENABLED"));
            metrics.put("records", mapper.countRecords(null, null));
            metrics.put("successRecords", mapper.countRecordsBySuccess(true));
            metrics.put("failedRecords", mapper.countRecordsBySuccess(false));
        } catch (Exception ignored) {
            return zero(metrics);
        }
        return metrics;
    }

    private Map<String, Long> excelMetrics() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("total", 0L);
        metrics.put("success", 0L);
        metrics.put("failed", 0L);
        metrics.put("import", 0L);
        metrics.put("export", 0L);
        ExcelAdminMapper mapper = available(excelAdminMapperProvider);
        if (mapper == null) {
            return metrics;
        }
        try {
            metrics.put("total", mapper.countAllTasks());
            metrics.put("success", mapper.countTasksByStatus("SUCCESS"));
            metrics.put("failed", mapper.countTasksByStatus("FAILED"));
            metrics.put("import", mapper.countTasksByType("IMPORT"));
            metrics.put("export", mapper.countTasksByType("EXPORT"));
        } catch (Exception ignored) {
            return zero(metrics);
        }
        return metrics;
    }

    private Map<String, Long> fileMetrics() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("active", 0L);
        metrics.put("deleted", 0L);
        metrics.put("totalSize", 0L);
        FileAdminRepository repository = available(fileAdminRepositoryProvider);
        if (repository == null) {
            return metrics;
        }
        try {
            metrics.putAll(repository.stats());
        } catch (Exception ignored) {
            return zero(metrics);
        }
        return metrics;
    }

    private Map<String, Long> zero(Map<String, Long> metrics) {
        metrics.replaceAll((key, value) -> 0L);
        return metrics;
    }

    private <T> T available(ObjectProvider<T> provider) {
        if (provider == null) {
            return null;
        }
        try {
            return provider.getIfAvailable();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private DashboardController.SecurityStatus securityStatus() {
        AdminSystemRepository repository = available(adminSystemRepositoryProvider);
        if (repository == null) {
            return new DashboardController.SecurityStatus(true);
        }
        try {
            boolean changed = repository.listConfigs().stream()
                    .filter(config -> "admin.default.password.changed".equals(config.getConfigKey()))
                    .map(ConfigItem::getConfigValue)
                    .findFirst()
                    .map(Boolean::parseBoolean)
                    .orElse(false);
            return new DashboardController.SecurityStatus(changed);
        } catch (Exception ignored) {
            return new DashboardController.SecurityStatus(true);
        }
    }

    private List<DashboardController.ModuleStatus> moduleStatuses() {
        return List.of(
                module("framework-core", "com.framework.core.trace.TraceContext"),
                module("framework-web", "com.framework.web.config.TraceIdFilter"),
                module("framework-auth", "com.framework.auth.jwt.JwtUtils"),
                module("framework-security", "com.framework.security.aspect.PermissionAspect"),
                module("framework-cache", "com.framework.cache.config.CacheAutoConfiguration"),
                module("framework-lock", "com.framework.lock.config.LockAutoConfiguration"),
                module("framework-idempotent", "com.framework.idempotent.config.IdempotentAutoConfiguration"),
                module("framework-crypto", "com.framework.crypto.util.PasswordUtils"),
                module("framework-log", "com.framework.log.aspect.OperationLogAspect"),
                module("framework-rate-limiter", "com.framework.ratelimiter.config.RateLimiterAutoConfiguration"),
                module("framework-mq", "com.framework.mq.config.MqAutoConfiguration"),
                module("framework-retry", "com.framework.retry.config.RetryAutoConfiguration"),
                module("framework-tools", "com.framework.tools.tree.TreeUtils"),
                module("framework-notify", "com.framework.notify.config.NotifyAutoConfiguration"),
                module("framework-local-message", "com.framework.localmessage.service.LocalMessageService"),
                module("framework-excel", "com.framework.excel.config.ExcelAutoConfiguration"),
                module("framework-datasource", "com.framework.datasource.config.DatasourceAutoConfiguration"),
                module("framework-redis", "com.framework.redis.config.RedisAutoConfiguration"),
                module("framework-feign", "com.framework.feign.config.FeignAutoConfiguration"),
                module("framework-monitor", "com.framework.monitor.health.FrameworkHealthIndicator"),
                module("framework-file", "com.framework.file.service.FileStorageService"));
    }

    private DashboardController.ModuleStatus module(String name, String markerClass) {
        boolean loaded = ClassUtils.isPresent(markerClass, DashboardService.class.getClassLoader());
        return new DashboardController.ModuleStatus(name, loaded ? "UP" : "MISSING");
    }
}
