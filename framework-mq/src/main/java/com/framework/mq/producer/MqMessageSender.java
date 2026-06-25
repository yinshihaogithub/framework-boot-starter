package com.framework.mq.producer;

import com.framework.mq.config.MqProperties;
import com.framework.mq.core.MessageWrapper;

/**
 * Common sender contract for RabbitMQ, Kafka and RocketMQ providers.
 */
public interface MqMessageSender {

    MqProperties.Provider provider();

    <T> void send(String destination, String routingKey, MessageWrapper<T> wrapper);

    default <T> void send(String destination, String routingKey, T payload) {
        send(destination, routingKey, MessageWrapper.of(payload));
    }

    default <T> void send(String destination, String routingKey, String businessKey, T payload) {
        send(destination, routingKey, MessageWrapper.of(businessKey, payload));
    }

    default <T> void sendWithDelay(String destination, String routingKey, MessageWrapper<T> wrapper, long delayMs) {
        throw new UnsupportedOperationException(provider() + " sender does not support delay send");
    }
}
