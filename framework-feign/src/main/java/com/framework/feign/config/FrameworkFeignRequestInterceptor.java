package com.framework.feign.config;

import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Objects;

/**
 * Relays configured incoming request headers to outgoing Feign calls.
 */
public class FrameworkFeignRequestInterceptor implements RequestInterceptor {

    private final FeignProperties properties;

    public FrameworkFeignRequestInterceptor(FeignProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public void apply(RequestTemplate template) {
        HttpServletRequest request = currentRequest();
        for (String header : relayHeaders()) {
            if (!StringUtils.hasText(header)) {
                continue;
            }
            String headerName = FeignProperties.validateRelayHeaderName(header);
            String value = request == null ? null : request.getHeader(headerName);
            if (FrameworkConstants.TRACE_ID_HEADER.equalsIgnoreCase(headerName)) {
                value = TraceContext.normalizeTraceId(value);
                if (!StringUtils.hasText(value)) {
                    value = TraceContext.ensureTraceId();
                }
            }
            if (StringUtils.hasText(value)) {
                template.header(headerName, value);
            }
        }
    }

    private List<String> relayHeaders() {
        List<String> relayHeaders = properties.getRelayHeaders();
        return FeignProperties.normalizeRelayHeaders(relayHeaders);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
