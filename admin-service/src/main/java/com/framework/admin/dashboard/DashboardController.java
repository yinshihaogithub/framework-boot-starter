package com.framework.admin.dashboard;

import com.framework.core.result.Result;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.service.LocalMessageService;
import com.framework.log.mapper.OperationLogMapper;
import com.framework.mq.deadletter.DeadLetterHandler;
import com.framework.mq.deadletter.MqFailedMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台首页聚合数据。
 */
@RestController
@RequestMapping("/admin/dashboard")
@Tag(name = "Dashboard", description = "管理后台首页")
public class DashboardController {

    private final ObjectProvider<DeadLetterHandler> deadLetterHandlerProvider;
    private final ObjectProvider<LocalMessageService> localMessageServiceProvider;
    private final ObjectProvider<OperationLogMapper> operationLogMapperProvider;

    public DashboardController(ObjectProvider<DeadLetterHandler> deadLetterHandlerProvider,
                               ObjectProvider<LocalMessageService> localMessageServiceProvider,
                               ObjectProvider<OperationLogMapper> operationLogMapperProvider) {
        this.deadLetterHandlerProvider = deadLetterHandlerProvider;
        this.localMessageServiceProvider = localMessageServiceProvider;
        this.operationLogMapperProvider = operationLogMapperProvider;
    }

    @Operation(summary = "管理后台首页统计")
    @GetMapping
    public Result<DashboardSummary> summary() {
        return Result.success(new DashboardSummary(
                mqMetrics(),
                localMessageMetrics(),
                logMetrics(),
                moduleStatuses()));
    }

    private Map<String, Long> mqMetrics() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("pending", 0L);
        metrics.put("retrying", 0L);
        metrics.put("manual", 0L);
        metrics.put("exhausted", 0L);
        metrics.put("total", 0L);
        DeadLetterHandler handler = deadLetterHandlerProvider.getIfAvailable();
        if (handler == null) {
            return metrics;
        }
        var store = handler.getFailedMessageStore();
        metrics.put("pending", countMq(store, MqFailedMessage.STATUS_PENDING));
        metrics.put("retrying", countMq(store, MqFailedMessage.STATUS_RETRYING));
        metrics.put("manual", countMq(store, MqFailedMessage.STATUS_MANUAL));
        metrics.put("exhausted", countMq(store, MqFailedMessage.STATUS_EXHAUSTED));
        metrics.put("total", (long) store.size());
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
        LocalMessageService service = localMessageServiceProvider.getIfAvailable();
        if (service == null) {
            return metrics;
        }
        var messages = service.findAll();
        for (LocalMessageStatus status : LocalMessageStatus.values()) {
            long count = messages.stream()
                    .filter(message -> status == message.getStatus())
                    .count();
            metrics.put(status.name().toLowerCase(), count);
        }
        metrics.put("total", (long) messages.size());
        return metrics;
    }

    private Map<String, Long> logMetrics() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("total", 0L);
        OperationLogMapper mapper = operationLogMapperProvider.getIfAvailable();
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

    private List<ModuleStatus> moduleStatuses() {
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
                module("framework-file", "com.framework.file.service.FileStorageService"),
                module("framework-job", "com.framework.job.service.JobHandler"));
    }

    private ModuleStatus module(String name, String markerClass) {
        boolean loaded = ClassUtils.isPresent(markerClass, DashboardController.class.getClassLoader());
        return new ModuleStatus(name, loaded ? "UP" : "MISSING");
    }

    public record DashboardSummary(Map<String, Long> mq,
                                   Map<String, Long> localMessage,
                                   Map<String, Long> logs,
                                   List<ModuleStatus> modules) {
    }

    public record ModuleStatus(String name, String status) {
    }
}
