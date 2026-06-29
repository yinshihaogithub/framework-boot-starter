package com.framework.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.admin.support.AdminClientIpResolver;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.trace.TraceContext;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes mandatory admin audit events for management operations.
 */
@Slf4j
@Service
public class AdminAuditService {

    private static final int MAX_TEXT_LENGTH = 4000;
    private static final String MASKED_VALUE = "******";
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "password", "passwd", "pwd", "token", "secret", "credential",
            "privatekey", "apikey", "authorization"
    );

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public AdminAuditService(OperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
        record(request, module, action, operationType, params, true, null);
    }

    public void failure(HttpServletRequest request, String module, String action, String operationType,
                        Object params, Exception exception) {
        record(request, module, action, operationType, params, false,
                exception == null ? null : exception.getMessage());
    }

    private void record(HttpServletRequest request, String module, String action, String operationType,
                        Object params, boolean success, String errorMessage) {
        try {
            OperationLogEntity entity = new OperationLogEntity();
            entity.setLogType("OPERATION");
            entity.setModule(module);
            entity.setAction(action);
            entity.setOperationType(operationType);
            entity.setUri(request == null ? null : request.getRequestURI());
            entity.setHttpMethod(request == null ? null : request.getMethod());
            entity.setMethod("admin-service");
            entity.setParams(toJson(params));
            entity.setResult(success ? "{\"message\":\"success\"}" : null);
            entity.setSuccess(success);
            entity.setErrorMessage(truncate(errorMessage));
            entity.setElapsedMs(0L);
            entity.setOperatorId(UserContextHolder.getUserId());
            entity.setOperatorName(UserContextHolder.getUsername());
            entity.setClientIp(AdminClientIpResolver.resolve(request));
            entity.setTraceId(TraceContext.ensureTraceId());
            entity.setCreateTime(new Date());
            if (operationLogMapper == null) {
                throw new IllegalStateException("operation log mapper unavailable");
            }
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("[后台审计] 操作日志写入失败 module={}, action={}, type={}, success={}, error={}",
                    module, action, operationType, success, e.getMessage());
        }
    }

    private String toJson(Object params) {
        if (params == null) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(maskValue(normalize(params))));
        } catch (Exception e) {
            return "[序列化失败]";
        }
    }

    private Object normalize(Object value) {
        if (value == null
                || value instanceof Map<?, ?>
                || value instanceof Collection<?>
                || value.getClass().isArray()
                || value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>) {
            return value;
        }
        return objectMapper.convertValue(value, Object.class);
    }

    private Object maskValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> masked = new LinkedHashMap<>();
            source.forEach((key, item) -> {
                String name = String.valueOf(key);
                masked.put(name, isSensitiveKey(name) ? MASKED_VALUE : maskValue(item));
            });
            return masked;
        }
        if (value instanceof Collection<?> source) {
            List<Object> masked = new ArrayList<>(source.size());
            for (Object item : source) {
                masked.add(maskValue(item));
            }
            return masked;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> masked = new ArrayList<>(Array.getLength(value));
            for (int i = 0; i < Array.getLength(value); i++) {
                masked.add(maskValue(Array.get(value, i)));
            }
            return masked;
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return SENSITIVE_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    public Map<String, Object> params(Object... values) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            params.put(String.valueOf(values[i]), values[i + 1]);
        }
        return params;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH);
    }
}
