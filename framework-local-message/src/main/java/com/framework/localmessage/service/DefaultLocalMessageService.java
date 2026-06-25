package com.framework.localmessage.service;

import com.framework.localmessage.config.LocalMessageProperties;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.repository.LocalMessageRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        this.repository = repository;
        this.properties = properties;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(LocalMessageHandler::topic, Function.identity(), (left, right) -> left));
    }

    @Override
    public LocalMessage publish(String topic, String businessKey, String payload) {
        LocalMessage message = new LocalMessage()
                .setTopic(topic)
                .setBusinessKey(businessKey)
                .setPayload(payload)
                .setMaxRetry(properties.getMaxRetry())
                .setStatus(LocalMessageStatus.PENDING)
                .setNextRetryTime(LocalDateTime.now());
        return repository.save(message);
    }

    @Override
    public int retryDueMessages() {
        List<LocalMessage> messages = repository.findDueMessages(LocalDateTime.now(), properties.getBatchSize());
        int handled = 0;
        for (LocalMessage message : messages) {
            LocalMessageHandler handler = handlers.get(message.getTopic());
            if (handler == null) {
                continue;
            }
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
        message.setErrorMessage(exception.getMessage());
        if (retryCount >= message.getMaxRetry()) {
            message.setStatus(LocalMessageStatus.FAILED);
            message.setNextRetryTime(null);
        } else {
            message.setStatus(LocalMessageStatus.PENDING);
            message.setNextRetryTime(LocalDateTime.now().plus(properties.getRetryInterval()));
        }
        repository.save(message);
    }
}
