package com.framework.job.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class JobHandlerRegistry {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private JobHandlerRegistry() {
    }

    static Map<String, JobHandler> from(List<JobHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, JobHandler> registry = new LinkedHashMap<>();
        for (JobHandler handler : handlers) {
            if (handler == null) {
                throw new IllegalArgumentException("JobHandler must not be null");
            }
            String name = normalize(handler.name());
            if (!hasText(name)) {
                throw new IllegalArgumentException("JobHandler name must not be blank");
            }
            if (!NAME_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("JobHandler name must match [A-Za-z0-9._-]+: " + name);
            }
            if (registry.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate JobHandler name: " + name);
            }
            registry.put(name, handler);
        }
        return Collections.unmodifiableMap(registry);
    }

    static String normalize(String name) {
        return name == null ? null : name.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
