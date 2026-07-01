package com.framework.localmessage.service;

import com.framework.core.trace.TraceContext;
import com.framework.localmessage.config.LocalMessageProperties;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.repository.LocalMessageRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default local message service.
 */
public class DefaultLocalMessageService implements LocalMessageService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final LocalMessageRepository repository;
    private final LocalMessageProperties properties;
    private final Map<String, LocalMessageHandler> handlers;

    public DefaultLocalMessageService(LocalMessageRepository repository,
                                      LocalMessageProperties properties,
                                      List<LocalMessageHandler> handlers) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        validateProperties(properties);
        this.handlers = buildHandlers(handlers);
    }

    @Override
    public LocalMessage publish(String topic, String businessKey, String payload) {
        return publish(new LocalMessage()
                .setTopic(topic)
                .setBusinessKey(businessKey)
                .setPayload(payload));
    }

    @Override
    public LocalMessage publish(LocalMessage message) {
        prepareForPublish(message);
        return repository.save(message);
    }

    @Override
    public int retryDueMessages() {
        List<LocalMessage> messages = repository.findDueMessages(LocalDateTime.now(), properties.getBatchSize());
        int handled = 0;
        for (LocalMessage message : messages) {
            retryMessage(message);
            handled++;
        }
        return handled;
    }

    @Override
    public boolean retryNow(Long id) {
        requireId(id);
        Optional<LocalMessage> optionalMessage = repository.findById(id);
        if (optionalMessage.isEmpty()) {
            return false;
        }
        retryMessage(optionalMessage.get());
        return true;
    }

    @Override
    public void markSuccess(Long id) {
        requireId(id);
        repository.findById(id).ifPresent(message -> {
            LocalMessage updated = copyForUpdate(message);
            updated.setStatus(LocalMessageStatus.SUCCESS);
            updated.setErrorMessage(null);
            updated.setNextRetryTime(null);
            repository.save(updated);
        });
    }

    @Override
    public void markFailure(Long id, Exception exception) {
        requireId(id);
        repository.findById(id).ifPresent(message -> markFailure(message, exception));
    }

    @Override
    public Optional<LocalMessage> findById(Long id) {
        requireId(id);
        return repository.findById(id);
    }

    private void dispatch(LocalMessage message, LocalMessageHandler handler) {
        Long messageId = message.getId();
        LocalMessage processing = copyForUpdate(message);
        processing.setStatus(LocalMessageStatus.PROCESSING);
        processing.setNextRetryTime(null);
        repository.save(processing);
        Map<String, String> previousContext = TraceContext.copyContextMap();
        TraceContext.getOrCreateTraceId(processing.getTraceId());
        try {
            handler.handle(copyForHandler(processing));
            markSuccess(messageId);
        } catch (Exception e) {
            markFailure(processing, e);
        } finally {
            TraceContext.restore(previousContext);
        }
    }

    private LocalMessage copyForHandler(LocalMessage message) {
        return copyMessage(message);
    }

    private LocalMessage copyMessage(LocalMessage message) {
        return new LocalMessage()
                .setId(message.getId())
                .setMessageId(message.getMessageId())
                .setTraceId(message.getTraceId())
                .setParentMessageId(message.getParentMessageId())
                .setTopic(message.getTopic())
                .setBusinessKey(message.getBusinessKey())
                .setTenantId(message.getTenantId())
                .setOperator(message.getOperator())
                .setSource(message.getSource())
                .setPayload(message.getPayload())
                .setStatus(message.getStatus())
                .setRetryCount(message.getRetryCount())
                .setMaxRetry(message.getMaxRetry())
                .setNextRetryTime(message.getNextRetryTime())
                .setErrorMessage(message.getErrorMessage())
                .setCreateTime(message.getCreateTime())
                .setUpdateTime(message.getUpdateTime());
    }

    private void retryMessage(LocalMessage message) {
        String topic = normalize(message.getTopic());
        LocalMessageHandler handler = handlers.get(topic);
        LocalMessage updated = copyForUpdate(message);
        updated.setTopic(topic);
        if (handler == null) {
            markFailure(updated, new IllegalStateException("No LocalMessageHandler registered for topic: " + topic));
            return;
        }
        dispatch(updated, handler);
    }

    private void markFailure(LocalMessage message, Exception exception) {
        LocalMessage updated = copyForUpdate(message);
        int retryCount = updated.getRetryCount() + 1;
        updated.setRetryCount(retryCount);
        updated.setErrorMessage(errorMessage(exception));
        if (retryCount >= updated.getMaxRetry()) {
            updated.setStatus(LocalMessageStatus.FAILED);
            updated.setNextRetryTime(null);
        } else {
            updated.setStatus(LocalMessageStatus.PENDING);
            updated.setNextRetryTime(LocalDateTime.now().plus(properties.getRetryInterval()));
        }
        repository.save(updated);
    }

    private void prepareForPublish(LocalMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        if (!hasText(message.getTopic())) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        message.setTopic(normalize(message.getTopic()));
        normalizeContext(message);
        if (!hasText(message.getPayload())) {
            throw new IllegalArgumentException("payload must not be blank");
        }
        message.setStatus(LocalMessageStatus.PENDING);
        message.setRetryCount(0);
        if (message.getMaxRetry() <= 0) {
            message.setMaxRetry(properties.getMaxRetry());
        }
        message.setNextRetryTime(LocalDateTime.now());
        message.setErrorMessage(null);
    }

    private void normalizeContext(LocalMessage message) {
        String messageId = normalize(message.getMessageId());
        message.setMessageId(hasText(messageId) ? messageId : generateMessageId());
        message.setTraceId(resolveTraceId(message.getTraceId()));
        message.setParentMessageId(normalizeOptional(message.getParentMessageId()));
        message.setBusinessKey(normalizeOptional(message.getBusinessKey()));
        message.setTenantId(normalizeOptional(message.getTenantId()));
        message.setOperator(normalizeOptional(message.getOperator()));
        message.setSource(normalizeOptional(message.getSource()));
    }

    private String resolveTraceId(String traceId) {
        String normalizedTraceId = TraceContext.normalizeTraceId(normalize(traceId));
        if (hasText(normalizedTraceId)) {
            return normalizedTraceId;
        }
        normalizedTraceId = TraceContext.normalizeTraceId(normalize(TraceContext.getTraceId()));
        return hasText(normalizedTraceId) ? normalizedTraceId : TraceContext.generateTraceId();
    }

    private void validateProperties(LocalMessageProperties properties) {
        if (properties.getMaxRetry() <= 0) {
            throw new IllegalArgumentException("framework.local-message.max-retry must be greater than 0");
        }
        if (properties.getBatchSize() <= 0) {
            throw new IllegalArgumentException("framework.local-message.batch-size must be greater than 0");
        }
        if (properties.getRetryInterval() == null
                || properties.getRetryInterval().isZero()
                || properties.getRetryInterval().isNegative()) {
            throw new IllegalArgumentException("framework.local-message.retry-interval must be greater than 0");
        }
    }

    private LocalMessage copyForUpdate(LocalMessage message) {
        return copyMessage(message);
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("local message id must be greater than 0");
        }
    }

    private Map<String, LocalMessageHandler> buildHandlers(List<LocalMessageHandler> handlers) {
        Map<String, LocalMessageHandler> registry = new LinkedHashMap<>();
        if (handlers == null || handlers.isEmpty()) {
            return registry;
        }
        for (LocalMessageHandler handler : handlers) {
            if (handler == null) {
                throw new IllegalArgumentException("LocalMessageHandler must not be null");
            }
            String topic = normalize(handler.topic());
            if (!hasText(topic)) {
                throw new IllegalArgumentException("LocalMessageHandler topic must not be blank");
            }
            if (registry.containsKey(topic)) {
                throw new IllegalArgumentException("Duplicate LocalMessageHandler topic: " + topic);
            }
            registry.put(topic, handler);
        }
        return registry;
    }

    private String errorMessage(Exception exception) {
        if (exception == null) {
            return "unknown error";
        }
        String normalized = normalizeMessage(exception.getMessage());
        if (normalized == null) {
            normalized = exception.getClass().getName();
        }
        if (normalized.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String normalizeMessage(String message) {
        String text = normalizeOptional(message);
        return text == null ? null : text.replaceAll("[\\s\\p{Zs}]+", " ");
    }

    private String generateMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String normalize(String value) {
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
        return value.substring(start, end);
    }

    private static String normalizeOptional(String value) {
        String normalized = normalize(value);
        return hasText(normalized) ? normalized : null;
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

    private static boolean isBoundarySpace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }
}
