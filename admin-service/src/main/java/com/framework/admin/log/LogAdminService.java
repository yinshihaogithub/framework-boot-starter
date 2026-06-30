package com.framework.admin.log;

import com.framework.admin.system.AdminSystemModels.LoginLog;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.admin.support.AdminPageSupport;
import com.framework.admin.support.AdminTextSupport;
import com.framework.core.result.PageResult;
import com.framework.core.trace.TraceContext;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class LogAdminService {

    private static final Set<String> SUPPORTED_LOG_TYPES = Set.of("OPERATION", "API", "EXCEPTION");

    private final ObjectProvider<OperationLogMapper> mapperProvider;
    private final AdminSystemRepository systemRepository;

    public LogAdminService(ObjectProvider<OperationLogMapper> mapperProvider,
                           AdminSystemRepository systemRepository) {
        this.mapperProvider = mapperProvider;
        this.systemRepository = systemRepository;
    }

    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        OperationLogMapper mapper = availableMapper();
        stats.put("total", count(mapper, null, null, null, null, null));
        stats.put("operation", count(mapper, null, "OPERATION", null, null, null));
        stats.put("api", count(mapper, null, "API", null, null, null));
        stats.put("exception", count(mapper, null, "EXCEPTION", null, false, null));
        return stats;
    }

    public PageResult<OperationLogEntity> list(String module, String logType, Long operatorId,
                                               Boolean success, String traceId, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String safeModule = trimToNull(module);
        String safeLogType = normalizeLogTypeFilter(logType);
        String safeTraceId = TraceContext.normalizeTraceId(traceId);
        if (isInvalidLogTypeFilter(logType, safeLogType)) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        if (hasText(traceId) && safeTraceId == null) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        OperationLogMapper mapper = availableMapper();
        if (mapper == null) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        int offset = (safePageNum - 1) * safePageSize;
        try {
            List<OperationLogEntity> records = mapper.selectList(
                    safeModule, safeLogType, operatorId, success, safeTraceId, offset, safePageSize);
            long total = mapper.count(safeModule, safeLogType, operatorId, success, safeTraceId);
            return PageResult.of(records, total, safePageNum, safePageSize);
        } catch (Exception ignored) {
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public PageResult<OperationLogEntity> trace(String traceId, int pageNum, int pageSize) {
        return list(null, null, null, null, traceId, pageNum, pageSize);
    }

    public PageResult<LoginLog> loginLogs(String username, Boolean success, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String safeUsername = trimToNull(username);
        try {
            List<LoginLog> records = systemRepository.listLoginLogs(safeUsername, success, safePageNum, safePageSize);
            long total = systemRepository.countLoginLogs(safeUsername, success);
            return PageResult.of(records, total, safePageNum, safePageSize);
        } catch (RuntimeException e) {
            log.warn("[日志中心] 登录日志查询失败 error={}", e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    private String trimToNull(String value) {
        return AdminTextSupport.trimToNull(value);
    }

    private boolean hasText(String value) {
        return AdminTextSupport.hasText(value);
    }

    private String normalizeLogTypeFilter(String logType) {
        String text = trimToNull(logType);
        if (text == null) {
            return null;
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        return SUPPORTED_LOG_TYPES.contains(normalized) ? normalized : null;
    }

    private boolean isInvalidLogTypeFilter(String originalLogType, String normalizedLogType) {
        return hasText(originalLogType) && normalizedLogType == null;
    }

    private OperationLogMapper availableMapper() {
        try {
            return mapperProvider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("[日志中心] 操作日志存储不可用 error={}", e.getMessage());
            return null;
        }
    }

    private long count(OperationLogMapper mapper, String module, String logType,
                       Long operatorId, Boolean success, String traceId) {
        if (mapper == null) {
            return 0;
        }
        try {
            return mapper.count(module, logType, operatorId, success, traceId);
        } catch (Exception ignored) {
            return 0;
        }
    }

}
