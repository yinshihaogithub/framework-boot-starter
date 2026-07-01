package com.framework.localmessage.service;

import com.framework.localmessage.model.LocalMessage;

import java.util.Optional;

/**
 * Local message service.
 */
public interface LocalMessageService {

    default LocalMessage publish(String topic, String businessKey, String payload) {
        return publish(new LocalMessage()
                .setTopic(topic)
                .setBusinessKey(businessKey)
                .setPayload(payload));
    }

    LocalMessage publish(LocalMessage message);

    int retryDueMessages();

    boolean retryNow(Long id);

    void markSuccess(Long id);

    void markFailure(Long id, Exception exception);

    Optional<LocalMessage> findById(Long id);
}
