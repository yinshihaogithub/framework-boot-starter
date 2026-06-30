package com.framework.log.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.trace.TraceContext;
import com.framework.log.annotation.OperationLog;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.service.OperationLogStorageService;
import com.framework.log.util.LogDesensitizeUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 操作日志切面
 * - 异步记录，不阻塞业务
 * - 记录模块、操作、参数、结果、耗时、IP
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OperationLogStorageService storageService;

    public OperationLogAspect(OperationLogStorageService storageService) {
        this.storageService = storageService;
    }

    @Around("@annotation(com.framework.log.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationLog annotation = method.getAnnotation(OperationLog.class);

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            long finalElapsed = elapsed;
            Object finalResult = result;
            Throwable finalError = error;
            String traceId = TraceContext.getTraceId();
            RequestInfo requestInfo = getCurrentRequestInfo();
            Map<String, String> contextMap = TraceContext.copyContextMap();

            // 异步记录日志
            CompletableFuture.runAsync(TraceContext.wrap(() -> {
                try {
                    recordLog(annotation, method, joinPoint.getArgs(), finalResult,
                            finalError, finalElapsed, traceId, requestInfo);
                } catch (Exception e) {
                    log.error("[操作日志] 记录失败", e);
                }
            }, contextMap));
        }
    }

    private void recordLog(OperationLog annotation, Method method, Object[] args,
                           Object result, Throwable error, long elapsed,
                           String traceId, RequestInfo requestInfo) {
        OperationLogEntity entity = buildEntity(annotation, method, args, result, error, elapsed, traceId, requestInfo);
        Map<String, Object> logData = new HashMap<>();
        logData.put("module", entity.getModule());
        logData.put("action", entity.getAction());
        logData.put("logType", entity.getLogType());
        logData.put("operationType", entity.getOperationType());
        logData.put("method", entity.getMethod());
        logData.put("elapsedMs", entity.getElapsedMs());
        logData.put("success", entity.getSuccess());
        logData.put("traceId", entity.getTraceId());
        logData.put("uri", entity.getUri());
        logData.put("httpMethod", entity.getHttpMethod());
        logData.put("ip", entity.getClientIp());
        logData.put("params", entity.getParams());
        logData.put("result", entity.getResult());
        logData.put("error", entity.getErrorMessage());
        log.info("[操作日志] {}", toJson(logData));
        storageService.saveAsync(entity);
    }

    private OperationLogEntity buildEntity(OperationLog annotation, Method method, Object[] args,
                                           Object result, Throwable error, long elapsed,
                                           String traceId, RequestInfo requestInfo) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setLogType(error == null ? "OPERATION" : "EXCEPTION");
        entity.setModule(annotation.module());
        entity.setAction(annotation.action());
        entity.setOperationType(annotation.type().name());
        entity.setMethod(method.getDeclaringClass().getSimpleName() + "." + method.getName());
        entity.setSuccess(error == null);
        entity.setElapsedMs(elapsed);
        entity.setTraceId(TraceContext.normalizeTraceId(traceId));
        entity.setCreateTime(new Date());

        if (annotation.saveParam() && args != null && args.length > 0) {
            entity.setParams(serializeAndDesensitize(args));
        }
        if (annotation.saveResult() && result != null) {
            entity.setResult(serializeAndDesensitize(result));
        }
        if (error != null) {
            entity.setErrorMessage(error.getMessage());
        }
        if (requestInfo != null) {
            entity.setUri(requestInfo.uri());
            entity.setHttpMethod(requestInfo.method());
            entity.setClientIp(requestInfo.ip());
        }
        return entity;
    }

    private String serializeAndDesensitize(Object value) {
        try {
            return LogDesensitizeUtils.desensitize(OBJECT_MAPPER.writeValueAsString(value));
        } catch (Exception e) {
            return "[序列化失败]";
        }
    }

    private RequestInfo getCurrentRequestInfo() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        return new RequestInfo(request.getRequestURI(), request.getMethod(), getClientIp(request));
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return map.toString();
        }
    }

    private record RequestInfo(String uri, String method, String ip) {
    }
}
