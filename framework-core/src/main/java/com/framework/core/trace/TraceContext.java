package com.framework.core.trace;

import com.framework.core.constant.FrameworkConstants;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TraceId helpers backed by SLF4J MDC.
 */
public final class TraceContext {

    private static final int MAX_TRACE_ID_LENGTH = 128;
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]+");

    private TraceContext() {
    }

    public static String getTraceId() {
        return MDC.get(FrameworkConstants.TRACE_ID_MDC_KEY);
    }

    public static String ensureTraceId() {
        return getOrCreateTraceId(null);
    }

    public static String getOrCreateTraceId(String incomingTraceId) {
        String traceId = normalizeTraceId(incomingTraceId);
        if (!hasText(traceId)) {
            traceId = normalizeTraceId(getTraceId());
        }
        if (!hasText(traceId)) {
            traceId = generateTraceId();
        }
        MDC.put(FrameworkConstants.TRACE_ID_MDC_KEY, traceId);
        return traceId;
    }

    public static void putTraceId(String traceId) {
        String normalizedTraceId = normalizeTraceId(traceId);
        if (hasText(normalizedTraceId)) {
            MDC.put(FrameworkConstants.TRACE_ID_MDC_KEY, normalizedTraceId);
        }
    }

    public static void clear() {
        MDC.remove(FrameworkConstants.TRACE_ID_MDC_KEY);
    }

    public static Map<String, String> copyContextMap() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return contextMap == null ? null : new HashMap<>(contextMap);
    }

    public static Runnable wrap(Runnable task) {
        return wrap(task, copyContextMap());
    }

    public static Runnable wrap(Runnable task, Map<String, String> contextMap) {
        return () -> {
            Map<String, String> previousContextMap = MDC.getCopyOfContextMap();
            restore(contextMap);
            try {
                task.run();
            } finally {
                restore(previousContextMap);
            }
        };
    }

    public static void restore(Map<String, String> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(contextMap);
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean hasText(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isBoundarySpace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeTraceId(String value) {
        if (!hasText(value)) {
            return null;
        }
        String traceId = trimBoundarySpace(value);
        if (traceId.length() > MAX_TRACE_ID_LENGTH || !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            return null;
        }
        return traceId;
    }

    private static String trimBoundarySpace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isBoundarySpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isBoundarySpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isBoundarySpace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }
}
