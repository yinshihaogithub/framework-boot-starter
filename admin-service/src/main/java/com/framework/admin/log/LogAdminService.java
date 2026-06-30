package com.framework.admin.log;

import com.framework.admin.system.AdminSystemModels.LoginLog;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.admin.support.AdminPageSupport;
import com.framework.core.result.PageResult;
import com.framework.core.trace.TraceContext;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LogAdminService {

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
        String safeLogType = trimToNull(logType);
        String safeTraceId = TraceContext.normalizeTraceId(traceId);
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
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
