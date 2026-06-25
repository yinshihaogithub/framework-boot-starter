package com.framework.log.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.trace.TraceContext;
import com.framework.log.annotation.OperationLog;
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
        Map<String, Object> logData = new HashMap<>();
        logData.put("module", annotation.module());
        logData.put("action", annotation.action());
        logData.put("type", annotation.type().name());
        logData.put("method", method.getDeclaringClass().getSimpleName() + "." + method.getName());
        logData.put("elapsedMs", elapsed);
        logData.put("success", error == null);
        if (traceId != null && !traceId.isBlank()) {
            logData.put("traceId", traceId);
        }

        // 请求参数（脱敏后记录）
        if (annotation.saveParam() && args != null && args.length > 0) {
            try {
                String paramsJson = OBJECT_MAPPER.writeValueAsString(args);
                logData.put("params", LogDesensitizeUtils.desensitize(paramsJson));
            } catch (Exception e) {
                logData.put("params", "[序列化失败]");
            }
        }

        // 返回结果（脱敏后记录）
        if (annotation.saveResult() && result != null) {
            try {
                String resultJson = OBJECT_MAPPER.writeValueAsString(result);
                logData.put("result", LogDesensitizeUtils.desensitize(resultJson));
            } catch (Exception e) {
                logData.put("result", "[序列化失败]");
            }
        }

        // 错误信息
        if (error != null) {
            logData.put("error", error.getMessage());
        }

        if (requestInfo != null) {
            logData.put("uri", requestInfo.uri());
            logData.put("method", requestInfo.method());
            logData.put("ip", requestInfo.ip());
        }

        log.info("[操作日志] {}", toJson(logData));
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
