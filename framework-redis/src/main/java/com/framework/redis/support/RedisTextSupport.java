package com.framework.redis.support;

/**
 * Shared Redis text normalization helpers.
 */
public final class RedisTextSupport {

    private RedisTextSupport() {
    }

    public static String requireText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && isBoundarySpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isBoundarySpace(value.charAt(end - 1))) {
            end--;
        }
        return start == end ? null : value.substring(start, end);
    }

    public static boolean containsControlCharacter(String value) {
        return value != null && value.chars().anyMatch(Character::isISOControl);
    }

    private static boolean isBoundarySpace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }
}
