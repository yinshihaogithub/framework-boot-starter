package com.framework.log.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.trace.TraceContext;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.service.OperationLogStorageService;
import com.framework.log.util.LogDesensitizeUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * API 日志过滤器
 * 按采样率记录请求/响应日志到 DB
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ApiLogFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    @Lazy
    private OperationLogStorageService storageService;

    @Override
    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 采样判断
        if (!storageService.shouldLogApi()) {
            chain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        String traceId = TraceContext.ensureTraceId();

        try {
            chain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;

            // 异步记录 API 日志
            try {
                OperationLogEntity entity = new OperationLogEntity();
                entity.setLogType("API");
                entity.setModule("API");
                entity.setAction(request.getMethod() + " " + request.getRequestURI());
                entity.setOperationType("API");
                entity.setUri(request.getRequestURI());
                entity.setHttpMethod(request.getMethod());
                entity.setMethod(request.getMethod());
                entity.setSuccess(response.getStatus() < 400);
                entity.setElapsedMs(elapsed);
                entity.setTraceId(traceId);
                entity.setClientIp(getClientIp(request));
                entity.setCreateTime(new java.util.Date());

                // 参数（query string）
                String queryString = request.getQueryString();
                if (queryString != null) {
                    entity.setParams(LogDesensitizeUtils.desensitize(queryString));
                }

                storageService.saveAsync(entity);
            } catch (Exception e) {
                log.debug("[API日志] 记录失败: {}", e.getMessage());
            }
        }
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
}
