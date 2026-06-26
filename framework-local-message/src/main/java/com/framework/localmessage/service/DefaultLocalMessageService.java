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
            String topic = normalize(message.getTopic());
            LocalMessageHandler handler = handlers.get(topic);
            if (handler == null) {
                continue;
            }
            message.setTopic(topic);
            handled++;
            dispatch(message, handler);
        }
        return handled;
    }

    @Override
    public void markSuccess(Long id) {
        repository.findById(id).ifPresent(message -> {
            message.setStatus(LocalMessageStatus.SUCCESS);
            message.setErrorMessage(null);
            repository.save(message);
        });
    }

    @Override
    public void markFailure(Long id, Exception exception) {
        repository.findById(id).ifPresent(message -> markFailure(message, exception));
    }

    @Override
    public Optional<LocalMessage> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<LocalMessage> findAll() {
        return repository.findAll();
    }

    private void dispatch(LocalMessage message, LocalMessageHandler handler) {
        message.setStatus(LocalMessageStatus.PROCESSING);
        repository.save(message);
        try {
            handler.handle(message);
            markSuccess(message.getId());
        } catch (Exception e) {
            markFailure(message, e);
        }
    }

    private void markFailure(LocalMessage message, Exception exception) {
        int retryCount = message.getRetryCount() + 1;
        message.setRetryCount(retryCount);
        message.setErrorMessage(errorMessage(exception));
        if (retryCount >= message.getMaxRetry()) {
            message.setStatus(LocalMessageStatus.FAILED);
            message.setNextRetryTime(null);
        } else {
            message.setStatus(LocalMessageStatus.PENDING);
            message.setNextRetryTime(LocalDateTime.now().plus(properties.getRetryInterval()));
        }
        repository.save(message);
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
        String normalizedTraceId = TraceContext.normalizeTraceId(traceId);
        if (hasText(normalizedTraceId)) {
            return normalizedTraceId;
        }
        normalizedTraceId = TraceContext.normalizeTraceId(TraceContext.getTraceId());
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
        return hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getName();
    }

    private String generateMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeOptional(String value) {
        String normalized = normalize(value);
        return hasText(normalized) ? normalized : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
