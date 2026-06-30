package com.framework.mq.producer;

import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;
import com.framework.mq.support.MqTextSupport;

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
        if (!MqTextSupport.hasText(wrapper.getMessageId())) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (!MqTextSupport.hasText(wrapper.getType())) {
            throw new IllegalArgumentException("message type must not be blank");
        }
        normalizeMetadata(wrapper);
        return wrapper;
    }

    static String requireText(String value, String name) {
        if (!MqTextSupport.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return MqTextSupport.trimBoundarySpace(value);
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
        if (!MqTextSupport.hasText(normalizedTraceId)) {
            normalizedTraceId = TraceContext.ensureTraceId();
        }
        wrapper.setTraceId(normalizedTraceId);
    }

    private static <T> void normalizeMetadata(MessageWrapper<T> wrapper) {
        wrapper.setMessageId(MqTextSupport.trimBoundarySpace(wrapper.getMessageId()));
        wrapper.setType(MqTextSupport.trimBoundarySpace(wrapper.getType()));
        wrapper.setBusinessKey(trimToNull(wrapper.getBusinessKey()));
        wrapper.setParentMessageId(trimToNull(wrapper.getParentMessageId()));
        wrapper.setSource(trimToNull(wrapper.getSource()));
    }

    static String trimToNull(String value) {
        return MqTextSupport.trimToNull(value);
    }
}
