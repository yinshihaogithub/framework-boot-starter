package com.framework.web.config;

import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 链路追踪过滤器：注入 traceId 到 MDC 和响应头
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String traceId = TraceContext.getOrCreateTraceId(req.getHeader(FrameworkConstants.TRACE_ID_HEADER));
        resp.setHeader(FrameworkConstants.TRACE_ID_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
