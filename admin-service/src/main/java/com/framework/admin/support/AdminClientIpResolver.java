package com.framework.admin.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves a safe client IP for admin logs.
 */
public final class AdminClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";

    private AdminClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            for (String item : forwarded.split(",")) {
                String candidate = normalize(item);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return normalize(request.getRemoteAddr());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (UNKNOWN.equalsIgnoreCase(normalized) || containsControlCharacter(normalized)) {
            return null;
        }
        return normalized;
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
