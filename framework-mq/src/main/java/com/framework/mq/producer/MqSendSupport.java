package com.framework.mq.producer;

import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;

import java.util.Objects;

final class MqSendSupport {

    private MqSendSupport() {
    }

    static <T> MessageWrapper<T> requireWrapper(MessageWrapper<T> wrapper) {
        if (wrapper == null) {
            throw new IllegalArgumentException("message wrapper must not be null");
        }
        if (wrapper.getPayload() == null) {
            throw new IllegalArgumentException("message payload must not be null");
        }
        if (!hasText(wrapper.getMessageId())) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (!hasText(wrapper.getType())) {
            throw new IllegalArgumentException("message type must not be blank");
        }
        normalizeMetadata(wrapper);
        return wrapper;
    }

    static void requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    static void requireNotNull(Object value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
    }

    static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    static <T> void fillTrace(MessageWrapper<T> wrapper) {
        requireWrapper(wrapper);
        String normalizedTraceId = TraceContext.normalizeTraceId(wrapper.getTraceId());
        if (!hasText(normalizedTraceId)) {
            normalizedTraceId = TraceContext.ensureTraceId();
        }
        wrapper.setTraceId(normalizedTraceId);
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

    private static <T> void normalizeMetadata(MessageWrapper<T> wrapper) {
        wrapper.setMessageId(trimBoundarySpace(wrapper.getMessageId()));
        wrapper.setType(trimBoundarySpace(wrapper.getType()));
        wrapper.setBusinessKey(trimToNull(wrapper.getBusinessKey()));
        wrapper.setParentMessageId(trimToNull(wrapper.getParentMessageId()));
        wrapper.setSource(trimToNull(wrapper.getSource()));
    }

    private static String trimToNull(String value) {
        return hasText(value) ? trimBoundarySpace(value) : null;
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
