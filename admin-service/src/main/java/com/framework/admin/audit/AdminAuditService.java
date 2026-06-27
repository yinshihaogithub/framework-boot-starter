package com.framework.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.trace.TraceContext;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes mandatory admin audit events for management operations.
 */
@Service
public class AdminAuditService {

    private static final int MAX_TEXT_LENGTH = 4000;

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public AdminAuditService(OperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
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
            entity.setClientIp(clientIp(request));
            entity.setTraceId(TraceContext.ensureTraceId());
            entity.setCreateTime(new Date());
            operationLogMapper.insert(entity);
        } catch (Exception ignored) {
            // Audit must never block the admin management flow.
        }
    }

    private String toJson(Object params) {
        if (params == null) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(params));
        } catch (JsonProcessingException e) {
            return truncate(String.valueOf(params));
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
