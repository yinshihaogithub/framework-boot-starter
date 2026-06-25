package com.framework.feign.config;

import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Relays configured incoming request headers to outgoing Feign calls.
 */
public class FrameworkFeignRequestInterceptor implements RequestInterceptor {

    private final FeignProperties properties;

    public FrameworkFeignRequestInterceptor(FeignProperties properties) {
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        HttpServletRequest request = currentRequest();
        for (String header : properties.getRelayHeaders()) {
            String value = request == null ? null : request.getHeader(header);
            if (!StringUtils.hasText(value) && FrameworkConstants.TRACE_ID_HEADER.equalsIgnoreCase(header)) {
                value = TraceContext.ensureTraceId();
            }
            if (StringUtils.hasText(value)) {
                template.header(header, value);
            }
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
