package com.framework.core.trace;

import com.framework.core.constant.FrameworkConstants;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TraceId helpers backed by SLF4J MDC.
 */
public final class TraceContext {

    private TraceContext() {
    }

    public static String getTraceId() {
        return MDC.get(FrameworkConstants.TRACE_ID_MDC_KEY);
    }

    public static String ensureTraceId() {
        return getOrCreateTraceId(null);
    }

    public static String getOrCreateTraceId(String incomingTraceId) {
        String traceId = hasText(incomingTraceId) ? incomingTraceId.trim() : getTraceId();
        if (!hasText(traceId)) {
            traceId = generateTraceId();
        }
        putTraceId(traceId);
        return traceId;
    }

    public static void putTraceId(String traceId) {
        if (hasText(traceId)) {
            MDC.put(FrameworkConstants.TRACE_ID_MDC_KEY, traceId.trim());
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
        return value != null && !value.isBlank();
    }
}
