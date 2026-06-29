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
        return value != null && !value.isBlank();
    }
}
