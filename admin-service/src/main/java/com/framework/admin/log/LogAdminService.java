package com.framework.admin.log;

import com.framework.admin.system.AdminSystemModels.LoginLog;
import com.framework.admin.system.AdminSystemMapperSupport;
import com.framework.admin.support.AdminPageSupport;
import com.framework.admin.support.AdminTextSupport;
import com.framework.core.result.PageResult;
import com.framework.core.result.ResultCode;
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
    private final AdminSystemMapperSupport systemMapperSupport;

    public LogAdminService(ObjectProvider<OperationLogMapper> mapperProvider,
                           AdminSystemMapperSupport systemMapperSupport) {
        this.mapperProvider = mapperProvider;
        this.systemMapperSupport = systemMapperSupport;
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

    public ActionResult<OperationLogEntity> detail(Long id) {
        ActionResult<OperationLogEntity> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        OperationLogMapper mapper = availableMapper();
        if (mapper == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "操作日志存储未启用");
        }
        try {
            OperationLogEntity logEntity = mapper.findById(id);
            if (logEntity == null) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "日志不存在");
            }
            return ActionResult.success(logEntity);
        } catch (Exception e) {
            log.warn("[日志中心] 日志详情查询失败 logId={}, error={}", id, e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "日志查询失败");
        }
    }

    public PageResult<LoginLog> loginLogs(String username, Boolean success, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String safeUsername = trimToNull(username);
        try {
            List<LoginLog> records = systemMapperSupport.listLoginLogs(safeUsername, success, safePageNum, safePageSize);
            long total = systemMapperSupport.countLoginLogs(safeUsername, success);
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

    private <T> ActionResult<T> invalidIdResult(Long id) {
        if (id == null || id <= 0) {
            return ActionResult.fail(ResultCode.PARAM_ERROR, "日志ID必须大于0");
        }
        return null;
    }

    private OperationLogMapper availableMapper() {
        if (mapperProvider == null) {
            return null;
        }
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

    public record ActionResult<T>(boolean success, int code, String message, T data) {
        public static <T> ActionResult<T> success(T data) {
            return new ActionResult<>(true, ResultCode.SUCCESS.getCode(), null, data);
        }

        public static <T> ActionResult<T> fail(ResultCode code, String message) {
            return new ActionResult<>(false, code.getCode(), message, null);
        }
    }

}
