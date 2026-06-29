package com.framework.feign.config;

import com.framework.core.constant.FrameworkConstants;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Feign module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.feign")
public class FeignProperties implements InitializingBean {

    private static final Pattern HTTP_HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");

    private boolean enabled = true;
    private List<String> relayHeaders = new ArrayList<>(List.of(
            FrameworkConstants.AUTH_HEADER,
            FrameworkConstants.TRACE_ID_HEADER,
            FrameworkConstants.TENANT_HEADER,
            FrameworkConstants.USER_ID_HEADER,
            FrameworkConstants.USER_NAME_HEADER,
            FrameworkConstants.USER_ROLES_HEADER,
            FrameworkConstants.USER_PERMISSIONS_HEADER
    ));

    @Override
    public void afterPropertiesSet() {
        if (relayHeaders == null || relayHeaders.isEmpty()) {
            relayHeaders = new ArrayList<>(List.of(FrameworkConstants.TRACE_ID_HEADER));
            return;
        }
        relayHeaders = normalizeRelayHeaders(relayHeaders);
    }

    static List<String> normalizeRelayHeaders(List<String> relayHeaders) {
        if (relayHeaders == null || relayHeaders.isEmpty()) {
            return List.of(FrameworkConstants.TRACE_ID_HEADER);
        }
        List<String> normalizedHeaders = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        boolean hasTraceHeader = false;
        for (String header : relayHeaders) {
            if (header == null || header.isBlank()) {
                continue;
            }
            String normalizedHeader = validateRelayHeaderName(header);
            if (FrameworkConstants.TRACE_ID_HEADER.equalsIgnoreCase(normalizedHeader)) {
                hasTraceHeader = true;
                normalizedHeader = FrameworkConstants.TRACE_ID_HEADER;
            }
            if (seen.add(normalizedHeader.toLowerCase(Locale.ROOT))) {
                normalizedHeaders.add(normalizedHeader);
            }
        }
        if (!hasTraceHeader) {
            normalizedHeaders.add(FrameworkConstants.TRACE_ID_HEADER);
        }
        return normalizedHeaders;
    }

    static String validateRelayHeaderName(String header) {
        String headerName = header.trim();
        if (!HTTP_HEADER_NAME.matcher(headerName).matches()) {
            throw new IllegalArgumentException("framework.feign.relay-headers contains invalid header name: "
                    + sanitizeHeaderName(header));
        }
        return headerName;
    }

    private static String sanitizeHeaderName(String header) {
        return header
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
