package com.framework.mq.support;

/**
 * Text normalization helpers for MQ configuration and send parameters.
 */
public final class MqTextSupport {

    private MqTextSupport() {
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
