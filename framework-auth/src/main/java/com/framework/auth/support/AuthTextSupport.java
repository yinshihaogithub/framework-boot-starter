package com.framework.auth.support;

/**
 * Text normalization helpers for authentication configuration.
 */
public final class AuthTextSupport {

    private AuthTextSupport() {
    }

    public static boolean hasText(String value) {
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

    public static String trimToNull(String value) {
        return hasText(value) ? trimBoundarySpace(value) : null;
    }

    public static String trimBoundarySpace(String value) {
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
