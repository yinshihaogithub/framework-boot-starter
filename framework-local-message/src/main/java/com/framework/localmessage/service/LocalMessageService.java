package com.framework.localmessage.service;

import com.framework.localmessage.model.LocalMessage;

import java.util.List;
import java.util.Optional;

/**
 * Local message service.
 */
public interface LocalMessageService {

    LocalMessage publish(String topic, String businessKey, String payload);

    default LocalMessage publish(LocalMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return publish(message.getTopic(), message.getBusinessKey(), message.getPayload());
    }

    int retryDueMessages();

    void markSuccess(Long id);

    void markFailure(Long id, Exception exception);

    Optional<LocalMessage> findById(Long id);

    List<LocalMessage> findAll();
}
