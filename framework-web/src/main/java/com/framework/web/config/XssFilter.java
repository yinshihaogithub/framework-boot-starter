package com.framework.web.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XSS 防护过滤器
 * 对请求参数和 Header 中的 HTML 特殊字符进行转义
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(new XssRequestWrapper((HttpServletRequest) request), response);
    }

    /**
     * 请求包装器：转义参数和 Header
     */
    static class XssRequestWrapper extends HttpServletRequestWrapper {

        public XssRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            return escape(super.getParameter(name));
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) {
                return null;
            }
            String[] escaped = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                escaped[i] = escape(values[i]);
            }
            return escaped;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> original = super.getParameterMap();
            Map<String, String[]> result = new LinkedHashMap<>();
            for (var entry : original.entrySet()) {
                String[] values = entry.getValue();
                String[] escaped = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    escaped[i] = escape(values[i]);
                }
                result.put(entry.getKey(), escaped);
            }
            return result;
        }

        /**
         * HTML 特殊字符转义
         */
        private String escape(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            StringBuilder sb = new StringBuilder(value.length() * 2);
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '<' -> sb.append("&lt;");
                    case '>' -> sb.append("&gt;");
                    case '&' -> sb.append("&amp;");
                    case '"' -> sb.append("&quot;");
                    case '\'' -> sb.append("&#x27;");
                    case '/' -> sb.append("&#x2F;");
                    default -> sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
