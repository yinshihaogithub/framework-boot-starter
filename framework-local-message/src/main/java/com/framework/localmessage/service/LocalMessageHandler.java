package com.framework.localmessage.service;

import com.framework.localmessage.model.LocalMessage;

/**
 * Handles local messages by topic.
 */
public interface LocalMessageHandler {

    String topic();

    void handle(LocalMessage message) throws Exception;
}
